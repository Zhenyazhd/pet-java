package com.jobsearch.core_api.vacancy;

import com.jobsearch.core_api.ai.ChatResult;
import com.jobsearch.core_api.ai.OpenRouterClient;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.UniqueConstraint;
import com.jobsearch.core_api.config.AppProperties;
import com.jobsearch.core_api.jobapplication.JobApplication;
import com.jobsearch.core_api.jobs.Job;
import com.jobsearch.core_api.jobs.JobHandler;
import com.jobsearch.core_api.jobs.JobOutcome;
import com.jobsearch.core_api.jobs.JobType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns a queued paste (URL + raw job posting) into a vacancy: the LLM extracts title, company,
 * description and requirements, then the vacancy is saved with a NOT_APPLIED application.
 * The job result is {@code {"vacancyId": ...}}.
 */
@Component
public class VacancyImportHandler implements JobHandler {

	private static final Logger log = LoggerFactory.getLogger(VacancyImportHandler.class);
	private static final int MAX_REQUIREMENTS = 40;
	/** Keeps the prompt affordable; the request itself allows up to 50,000 chars. */
	private static final int MAX_PROMPT_PASTE_CHARS = 40_000;

	private static final String AI_UNAVAILABLE_MESSAGE = "The AI service is not available right now. Please try again later.";
	private static final String AI_REJECTED_MESSAGE = "The AI could not process this posting. Try a shorter or cleaner text.";

	private static final String SYSTEM_PROMPT = """
			You extract structured job-posting fields from messy pasted text.
			Return ONLY a JSON object with this exact shape:
			{
			  "title": "job title string",
			  "company": "company name or null if unknown",
			  "description": "cleaned job description text suitable for later matching",
			  "requirements": [
			    { "name": "skill or requirement", "required": true }
			  ]
			}
			Rules:
			- title is required; if unclear, invent a short best-effort title from the text
			- company may be null when not stated
			- description should keep the useful posting content (role, requirements, responsibilities)
			  without site chrome, cookie banners, or navigation fluff
			- requirements: extract concrete skills / tools / qualifications mentioned in the paste
			  (languages, frameworks, cloud, soft skills only if explicitly listed as requirements)
			- required=true for must-have / required / mandatory items; required=false for nice-to-have / preferred / plus
			- if the posting does not distinguish, default required=true
			- do not invent skills that are not in the paste
			- keep name short (skill label), max ~40 items, deduplicate
			- keep text in the same language as the paste where possible; skill names may stay as written
			""";

	private final VacancyRepository vacancyRepository;
	private final OpenRouterClient openRouterClient;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transactionTemplate;
	private final Duration lease;

	public VacancyImportHandler(
			VacancyRepository vacancyRepository,
			OpenRouterClient openRouterClient,
			ObjectMapper objectMapper,
			PlatformTransactionManager transactionManager,
			AppProperties appProperties
	) {
		this.vacancyRepository = vacancyRepository;
		this.openRouterClient = openRouterClient;
		this.objectMapper = objectMapper;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		// The OpenRouter call may take the whole read timeout; the slack covers saving the vacancy.
		this.lease = Duration.ofSeconds(appProperties.getOpenRouter().getReadTimeoutSeconds() + 30);
	}

	@Override
	public JobType type() {
		return JobType.VACANCY_IMPORT;
	}

	/** One OpenRouter call each; short enough to run a few at once without a large LLM bill. */
	@Override
	public int concurrency() {
		return 4;
	}

	@Override
	public int maxAttempts() {
		return 2;
	}

	@Override
	public Duration lease() {
		return lease;
	}

	/**
	 * Makes sure the user has a vacancy for the URL and returns its id. Safe to run again: a vacancy
	 * that already exists (saved by an earlier attempt whose outcome was lost, or by the user while the
	 * job waited) is the result, without another paid LLM call.
	 */
	@Override
	public JobOutcome run(Job job) {
		VacancyImportPayload payload;
		try {
			payload = objectMapper.readValue(job.getPayload(), VacancyImportPayload.class);
		}
		catch (JacksonException ex) {
			log.error("Vacancy import payload unreadable jobId={}", job.getId(), ex);
			return new JobOutcome.Failed("bad_payload", "This request could not be processed.");
		}
		Vacancy existing = vacancyRepository.findByUserIdAndUrl(job.getUserId(), payload.url()).orElse(null);
		if (existing != null) {
			log.info("Vacancy import jobId={} found vacancyId={} for the URL, nothing to import",
					job.getId(), existing.getId());
			return succeeded(existing.getId());
		}
		// Each try is a paid LLM call; only failures that can pass on a second try are retried.
		return switch (openRouterClient.complete(SYSTEM_PROMPT, userPrompt(payload), null)) {
			case ChatResult.Answer(String answer) -> save(job, payload, parse(answer, payload.pastedText()));
			case ChatResult.Busy(Duration retryAfter) -> new JobOutcome.Busy(retryAfter);
			// A posting too long for the model, or an answer cut off at max_tokens: the same text fails again, and
			// a vacancy built from a truncated extraction is worse than asking for a shorter paste.
			case ChatResult.Rejected _ -> new JobOutcome.Failed("ai_rejected", AI_REJECTED_MESSAGE);
			case ChatResult.Misconfigured _ -> new JobOutcome.Failed("ai_unavailable", AI_UNAVAILABLE_MESSAGE);
			case ChatResult.Unavailable(String reason) -> new JobOutcome.Retry(reason);
		};
	}

	private JobOutcome save(Job job, VacancyImportPayload payload, ParsedVacancy parsed) {
		try {
			return succeeded(saveVacancy(job.getUserId(), payload.url(), parsed));
		}
		catch (ConflictException ex) {
			// The same URL was saved while the LLM was answering; that vacancy is the result.
			return vacancyRepository.findByUserIdAndUrl(job.getUserId(), payload.url())
					.map(vacancy -> succeeded(vacancy.getId()))
					.orElseGet(() -> new JobOutcome.Retry("vacancy for the URL was deleted after the conflict"));
		}
	}

	private JobOutcome succeeded(long vacancyId) {
		return new JobOutcome.Succeeded(objectMapper.writeValueAsString(Map.of("vacancyId", vacancyId)));
	}

	private long saveVacancy(long userId, String url, ParsedVacancy parsed) {
		return UniqueConstraint.onConflict(
				"vacancy URL conflict",
				() -> transactionTemplate.execute(status -> {
					Vacancy vacancy = new Vacancy();
					vacancy.setUserId(userId);
					vacancy.setUrl(url);
					vacancy.setTitle(parsed.title());
					vacancy.setCompany(parsed.company());
					vacancy.setDescription(parsed.description());
					for (VacancyRequirements.Item item : parsed.requirements()) {
						vacancy.addRequirement(item.name(), item.required());
					}
					vacancy.setApplication(new JobApplication());

					Vacancy saved = vacancyRepository.saveAndFlush(vacancy);
					log.info(
							"Imported vacancy id={} userId={} title={} company={} requirements={} applicationStatus=NOT_APPLIED",
							saved.getId(),
							userId,
							saved.getTitle(),
							saved.getCompany(),
							parsed.requirements().size()
					);
					return saved.getId();
				}),
				UniqueConstraint.VACANCY_USER_URL
		);
	}

	private static String userPrompt(VacancyImportPayload payload) {
		String pasted = payload.pastedText();
		return """
				Job URL: %s

				Pasted job posting:
				%s
				""".formatted(payload.url(), pasted.substring(0, Math.min(pasted.length(), MAX_PROMPT_PASTE_CHARS)));
	}

	/** Reads the LLM's JSON answer; anything unusable falls back to the raw paste. */
	private ParsedVacancy parse(String answer, String pasted) {
		JsonNode root;
		try {
			root = objectMapper.readTree(answer);
		}
		catch (JacksonException ex) {
			log.warn("Vacancy LLM JSON parse failed, falling back to paste: {}", ex.getMessage());
			return fallback(pasted);
		}

		String title = textOrNull(root.path("title"));
		String company = textOrNull(root.path("company"));
		String description = textOrNull(root.path("description"));
		return new ParsedVacancy(
				cut255(title != null ? title : fallbackTitle(pasted)),
				company != null ? cut255(company) : null,
				description != null ? description : pasted,
				parseRequirements(root.path("requirements"))
		);
	}

	private static List<VacancyRequirements.Item> parseRequirements(JsonNode node) {
		if (!node.isArray()) {
			return List.of();
		}
		List<VacancyRequirements.Item> collected = new ArrayList<>();
		for (JsonNode item : node) {
			boolean required = !item.path("required").isBoolean() || item.path("required").asBoolean(true);
			collected.add(new VacancyRequirements.Item(textOrNull(item.path("name")), required));
		}
		return VacancyRequirements.dedupe(collected, MAX_REQUIREMENTS);
	}

	private static ParsedVacancy fallback(String pasted) {
		return new ParsedVacancy(fallbackTitle(pasted), null, pasted, List.of());
	}

	private static String fallbackTitle(String pasted) {
		return cut255(pasted.lines()
				.map(String::strip)
				.filter(line -> !line.isBlank())
				.findFirst()
				.orElse("Untitled role"));
	}

	/** Fits {@code vacancy.title} / {@code vacancy.company} (VARCHAR(255)). */
	private static String cut255(String value) {
		return value.length() > 255 ? value.substring(0, 255).strip() : value;
	}

	private static String textOrNull(JsonNode node) {
		if (node.isNull() || node.isMissingNode()) {
			return null;
		}
		String value = node.asString("").strip();
		if (value.isBlank() || "null".equalsIgnoreCase(value)) {
			return null;
		}
		return value;
	}

	private record ParsedVacancy(
			String title,
			String company,
			String description,
			List<VacancyRequirements.Item> requirements
	) {
	}
}
