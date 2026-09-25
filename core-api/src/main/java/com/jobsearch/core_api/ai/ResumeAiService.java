package com.jobsearch.core_api.ai;

import com.jobsearch.core_api.ai.AiDtos.ChatTurn;
import com.jobsearch.core_api.ai.AiDtos.SuggestRequest;
import com.jobsearch.core_api.ai.AiDtos.SuggestResponse;
import com.jobsearch.core_api.profile.ProfileService;
import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import com.jobsearch.core_api.resume.ResumeService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Orchestrates per-section resume AI chats: builds prompts from resume + profile/career path,
 * calls OpenRouter, and normalizes {message, proposed} for the frontend.
 */
@Service
public class ResumeAiService {

	private static final Logger log = LoggerFactory.getLogger(ResumeAiService.class);

	private static final Set<String> SECTIONS = Set.of(
			"header",
			"profile",
			"experience",
			"education",
			"achievements",
			"skills"
	);

	private static final Set<String> INDEXABLE = Set.of(
			"experience",
			"education",
			"achievements",
			"skills"
	);

	private static final String SYSTEM = """
			You are a helpful resume assistant chatting about ONE resume section.
			Return ONLY a JSON object with exactly these keys:
			{
			  "message": "natural reply to the user",
			  "proposed": null
			}

			Critical rules for "proposed":
			- Default is null.
			- Set "proposed" ONLY if the user clearly asks you to change/rewrite/improve/update the section text,
			  OR you are offering a concrete revision they can apply.
			- Greetings, thanks, questions, opinions, explanations, brainstorming → proposed MUST be null.
			- If you are not changing the content, proposed MUST be null. Never echo the current section as proposed.
			- When proposed is set, it must be the FULL revised section value in the SAME JSON shape as the input.

			Source-of-truth rules:
			- If a Career path biography is provided, treat it as the user's factual background.
			- Prefer facts from Career path (+ profile name/email) over inventing new employers, degrees, dates, or skills.
			- You may rephrase and select relevant facts for the current section, but do not invent history.
			- If Career path is missing, say so when the user asks you to generate content from it, and keep facts truthful.

			Vacancy targeting:
			- If a Target vacancy description is provided, tailor wording, emphasis, and keyword alignment toward that role.
			- Still do not invent experience that is not in Career path / current section — only reframe what is true.
			- When vacancy context is present and the user asks to improve the section, prefer relevance to that vacancy.

			"message" rules:
			- Reply naturally in the user's language (e.g. Russian greeting → Russian reply).
			- Do not reply with only "OK".
			""";

	private final OpenRouterClient openRouterClient;
	private final ResumeService resumeService;
	private final ProfileService profileService;
	private final ObjectMapper objectMapper;

	public ResumeAiService(
			OpenRouterClient openRouterClient,
			ResumeService resumeService,
			ProfileService profileService,
			ObjectMapper objectMapper
	) {
		this.openRouterClient = openRouterClient;
		this.resumeService = resumeService;
		this.profileService = profileService;
		this.objectMapper = objectMapper;
	}

	public SuggestResponse suggest(SuggestRequest request) {
		String section = request.section().trim().toLowerCase();
		if (!SECTIONS.contains(section)) {
			throw new IllegalArgumentException(
					"Unknown section '" + request.section() + "'. Use one of: " + SECTIONS
			);
		}

		ResumeDocument resume = resumeService.get();
		Integer itemIndex = request.itemIndex();
		JsonNode current = sectionValue(resume, section, itemIndex);
		var profile = profileService.getProfile();
		String careerPath = profile.careerPath();
		boolean hasCareerPath = careerPath != null && !careerPath.isBlank();
		String vacancyContext = request.vacancyContext();
		boolean hasVacancy = vacancyContext != null && !vacancyContext.isBlank();

		String scope = itemIndex == null
				? section
				: section + "[" + itemIndex + "]";

		log.info(
				"AI suggest section={} itemIndex={} hasCareerPath={} hasVacancy={} historyTurns={}",
				section,
				itemIndex,
				hasCareerPath,
				hasVacancy,
				request.history() == null ? 0 : request.history().size()
		);
		StringBuilder historyBlock = new StringBuilder();
		List<ChatTurn> history = request.history();
		if (history != null && !history.isEmpty()) {
			historyBlock.append("Recent conversation:\n");
			for (ChatTurn turn : history) {
				historyBlock.append("- ")
						.append(turn.role())
						.append(": ")
						.append(turn.content())
						.append('\n');
			}
			historyBlock.append('\n');
		}

		String profileBlock = """
				User profile:
				- Name: %s
				- Email: %s
				""".formatted(
				blankToNone(profile.displayName()),
				blankToNone(profile.email())
		);

		String careerBlock = hasCareerPath
				? """
				Career path (PRIMARY factual biography — use this when drafting or improving the section):
				%s
				""".formatted(careerPath.strip())
				: """
				Career path: (not filled in yet — do not invent a biography; work only with the current section and user message)
				""";

		String vacancyBlock = hasVacancy
				? """
				Target vacancy (role the user is applying to — tailor emphasis and keywords toward this):
				%s
				""".formatted(vacancyContext.strip())
				: """
				Target vacancy: (not provided — keep the section generally strong; do not invent a job posting)
				""";

		String userPrompt = """
				Section: %s

				Current section JSON (what is on the CV sheet now):
				%s

				%s
				%s
				%s
				%sUser message:
				%s

				Remember: if this is just chat (hello, question, feedback) set proposed to null.
				When editing, ground new content in the Career path when it is available.
				When a Target vacancy is provided, align the section with that role without inventing facts.
				""".formatted(
				scope,
				objectMapper.writeValueAsString(current),
				profileBlock,
				careerBlock,
				vacancyBlock,
				historyBlock,
				request.instruction().strip()
		);

		String content = openRouterClient.chat(SYSTEM, userPrompt);
		JsonNode parsed = objectMapper.readTree(content);
		String message = parsed.path("message").asString("").strip();
		if (message.isBlank()) {
			message = "…";
		}

		JsonNode proposed = parsed.get("proposed");
		// Drop no-op proposals (model sometimes echoes the current section).
		if (proposed == null || proposed.isNull() || proposed.isMissingNode() || proposed.equals(current)) {
			proposed = null;
		}

		log.info("AI suggest done section={} hasProposed={}", section, proposed != null);
		return new SuggestResponse(section, itemIndex, message, proposed);
	}

	private JsonNode sectionValue(ResumeDocument resume, String section, Integer itemIndex) {
		if (itemIndex != null) {
			if (!INDEXABLE.contains(section)) {
				throw new IllegalArgumentException("itemIndex is only supported for: " + INDEXABLE);
			}
			List<?> list = listFor(resume, section);
			if (itemIndex < 0 || itemIndex >= list.size()) {
				throw new IllegalArgumentException(
						"itemIndex out of range for " + section + ": " + itemIndex
				);
			}
			return objectMapper.valueToTree(list.get(itemIndex));
		}

		return switch (section) {
			case "header" -> objectMapper.valueToTree(Map.of(
					"name", resume.name(),
					"headline", resume.headline(),
					"phone", resume.phone(),
					"email", resume.email(),
					"linkedinUrl", resume.linkedinUrl(),
					"linkedinLabel", resume.linkedinLabel()
			));
			case "profile" -> objectMapper.valueToTree(resume.profile());
			case "experience" -> objectMapper.valueToTree(resume.experience());
			case "education" -> objectMapper.valueToTree(resume.education());
			case "achievements" -> objectMapper.valueToTree(resume.achievements());
			case "skills" -> objectMapper.valueToTree(resume.skills());
			default -> throw new IllegalArgumentException("Unknown section: " + section);
		};
	}

	private static List<?> listFor(ResumeDocument resume, String section) {
		return switch (section) {
			case "experience" -> resume.experience();
			case "education" -> resume.education();
			case "achievements" -> resume.achievements();
			case "skills" -> resume.skills();
			default -> throw new IllegalArgumentException("Not a list section: " + section);
		};
	}

	private static String blankToNone(String value) {
		return value == null || value.isBlank() ? "(none)" : value.strip();
	}
}
