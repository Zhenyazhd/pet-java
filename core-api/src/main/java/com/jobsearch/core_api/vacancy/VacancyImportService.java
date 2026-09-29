package com.jobsearch.core_api.vacancy;

import com.jobsearch.core_api.ai.OpenRouterClient;
import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.jobapplication.ApplicationStatus;
import com.jobsearch.core_api.jobapplication.JobApplication;
import com.jobsearch.core_api.vacancy.VacancyDtos.VacancyImportRequest;
import com.jobsearch.core_api.vacancy.VacancyDtos.VacancyResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Imports a vacancy from URL + pasted JD text via OpenRouter, then persists
 * vacancy + requirements + application (NOT_APPLIED).
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

	public VacancyImportService(
			VacancyRepository vacancyRepository,
			VacancyService vacancyService,
			OpenRouterClient openRouterClient,
			ObjectMapper objectMapper,
			CurrentUserService currentUserService
	) {
		this.vacancyRepository = vacancyRepository;
		this.vacancyService = vacancyService;
		this.openRouterClient = openRouterClient;
		this.objectMapper = objectMapper;
		this.currentUserService = currentUserService;
	}

	@Transactional
	public VacancyResponse importFromPaste(VacancyImportRequest request) {
		long userId = currentUserService.requireUserId();
		String url = request.url().strip();
		String pasted = request.pastedText().strip();
		if (vacancyRepository.existsByUserIdAndUrl(userId, url)) {
			throw new ConflictException("Vacancy with this URL already exists");
		}

		ParsedVacancy parsed = parseWithLlm(url, pasted);

		Vacancy vacancy = new Vacancy();
		vacancy.setUserId(userId);
		vacancy.setUrl(url);
		vacancy.setTitle(parsed.title());
		vacancy.setCompany(parsed.company());
		vacancy.setDescription(parsed.description());
		vacancy.setMatchPercent(null);

		for (ParsedRequirement item : parsed.requirements()) {
			VacancyRequirement requirement = new VacancyRequirement();
			requirement.setName(item.name());
			requirement.setRequired(item.required());
			vacancy.addRequirement(requirement);
		}

		JobApplication application = new JobApplication();
		application.setStatus(ApplicationStatus.NOT_APPLIED);
		vacancy.setApplication(application);

		Vacancy saved = vacancyRepository.save(vacancy);
		log.info(
				"Imported vacancy id={} userId={} title={} company={} requirements={} applicationStatus=NOT_APPLIED",
				saved.getId(),
				userId,
				saved.getTitle(),
				saved.getCompany(),
				parsed.requirements().size()
		);
		return vacancyService.findById(saved.getId());
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
		List<ParsedRequirement> requirements = parseRequirements(root.path("requirements"));

		if (title == null || title.isBlank()) {
			title = fallbackTitle(pasted);
		}
		if (title.length() > 255) {
			title = title.substring(0, 255).strip();
		}
		if (company != null && company.length() > 255) {
			company = company.substring(0, 255).strip();
		}
		if (description == null || description.isBlank()) {
			description = pasted;
		}

		return new ParsedVacancy(title.strip(), company, description.strip(), requirements);
	}

	private static List<ParsedRequirement> parseRequirements(JsonNode node) {
		if (node == null || !node.isArray()) {
			return List.of();
		}
		Map<String, ParsedRequirement> unique = new LinkedHashMap<>();
		for (JsonNode item : node) {
			if (unique.size() >= MAX_REQUIREMENTS) {
				break;
			}
			String name = textOrNull(item.path("name"));
			if (name == null || name.isBlank()) {
				continue;
			}
			name = name.strip();
			if (name.length() > 255) {
				name = name.substring(0, 255).strip();
			}
			String key = name.toLowerCase(Locale.ROOT);
			boolean required = !item.path("required").isBoolean() || item.path("required").asBoolean(true);
			// if duplicate: keep required=true if either says required
			ParsedRequirement existing = unique.get(key);
			if (existing != null) {
				unique.put(key, new ParsedRequirement(existing.name(), existing.required() || required));
			}
			else {
				unique.put(key, new ParsedRequirement(name, required));
			}
		}
		return new ArrayList<>(unique.values());
	}

	private static ParsedVacancy fallback(String pasted) {
		return new ParsedVacancy(fallbackTitle(pasted), null, pasted, List.of());
	}

	private static String fallbackTitle(String pasted) {
		String firstLine = pasted.lines()
				.map(String::strip)
				.filter(line -> !line.isBlank())
				.findFirst()
				.orElse("Untitled role");
		if (firstLine.length() > 255) {
			return firstLine.substring(0, 255).strip();
		}
		return firstLine;
	}

	private static String textOrNull(JsonNode node) {
		if (node == null || node.isNull() || node.isMissingNode()) {
			return null;
		}
		String value = node.asString("").strip();
		if (value.isBlank() || "null".equalsIgnoreCase(value)) {
			return null;
		}
		return value;
	}

	private record ParsedRequirement(String name, boolean required) {
	}

	private record ParsedVacancy(
			String title,
			String company,
			String description,
			List<ParsedRequirement> requirements
	) {
	}
}
