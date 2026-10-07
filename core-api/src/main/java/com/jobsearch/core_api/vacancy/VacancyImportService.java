package com.jobsearch.core_api.vacancy;

import com.jobsearch.core_api.ai.OpenRouterClient;
import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.UniqueConstraint;
import com.jobsearch.core_api.jobapplication.JobApplication;
import com.jobsearch.core_api.vacancy.VacancyDtos.VacancyImportRequest;
import com.jobsearch.core_api.vacancy.VacancyDtos.VacancyResponse;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Imports a vacancy from URL + pasted JD text via OpenRouter, then persists
 * vacancy + requirements + application (NOT_APPLIED).
 * LLM calls run outside a DB transaction so the pool connection is not held open.
 */
@Service
public class VacancyImportService {

	private static final Logger log = LoggerFactory.getLogger(VacancyImportService.class);
	private static final int MAX_REQUIREMENTS = 40;

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
	private final VacancyService vacancyService;
	private final OpenRouterClient openRouterClient;
	private final ObjectMapper objectMapper;
	private final CurrentUserService currentUserService;
	private final TransactionTemplate transactionTemplate;

	public VacancyImportService(
			VacancyRepository vacancyRepository,
			VacancyService vacancyService,
			OpenRouterClient openRouterClient,
			ObjectMapper objectMapper,
			CurrentUserService currentUserService,
			PlatformTransactionManager transactionManager
	) {
		this.vacancyRepository = vacancyRepository;
		this.vacancyService = vacancyService;
		this.openRouterClient = openRouterClient;
		this.objectMapper = objectMapper;
		this.currentUserService = currentUserService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public VacancyResponse importFromPaste(VacancyImportRequest request) {
		long userId = currentUserService.requireUserId();
		String url = request.url().strip();
		String pasted = request.pastedText().strip();
		if (vacancyRepository.existsByUserIdAndUrl(userId, url)) {
			throw new ConflictException("Vacancy with this URL already exists");
		}

		ParsedVacancy parsed = parseWithLlm(url, pasted);
		return persistImported(userId, url, parsed);
	}

	private VacancyResponse persistImported(long userId, String url, ParsedVacancy parsed) {
		return UniqueConstraint.onConflict(
				"Vacancy with this URL already exists",
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
					return vacancyService.toResponse(saved);
				}),
				UniqueConstraint.VACANCY_USER_URL
		);
	}

	private ParsedVacancy parseWithLlm(String url, String pasted) {
		String userPrompt = """
				Job URL: %s

				Pasted job posting:
				%s
				""".formatted(url, pasted.length() > 40_000 ? pasted.substring(0, 40_000) : pasted);

		String raw = openRouterClient.chat(SYSTEM_PROMPT, userPrompt, null);
		JsonNode root;
		try {
			root = objectMapper.readTree(raw);
		}
		catch (Exception ex) {
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
