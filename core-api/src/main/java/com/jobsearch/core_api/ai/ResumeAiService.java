package com.jobsearch.core_api.ai;

import com.jobsearch.core_api.ai.AiDtos.ChatTurn;
import com.jobsearch.core_api.ai.AiDtos.SuggestRequest;
import com.jobsearch.core_api.ai.AiDtos.SuggestResponse;
import com.jobsearch.core_api.common.ServiceUnavailableException;
import com.jobsearch.core_api.profile.ProfileService;
import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import com.jobsearch.core_api.resume.ResumeService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared resume AI chat: builds prompts from full resume or a focused section,
 * plus profile/career path / vacancy context, then normalizes {message, proposed}.
 */
@Service
public class ResumeAiService {

	private static final Logger log = LoggerFactory.getLogger(ResumeAiService.class);

	private static final String SCOPE_ALL = "all";

	private static final Set<String> SECTIONS = Set.of(
			"header",
			"profile",
			"experience",
			"education",
			"achievements",
			"skills"
	);

	private static final Set<String> ALLOWED_MODELS = Set.of(
			"openai/gpt-4o-mini",
			"openai/gpt-4o",
			"openai/gpt-4.1-mini",
			"openai/gpt-4.1",
			"openai/gpt-5-nano",
			"openai/gpt-5-mini",
			"openai/gpt-5",
			"anthropic/claude-sonnet-4",
			"google/gemini-2.5-flash",
			"deepseek/deepseek-chat"
	);

	private static final String SYSTEM = """
			You are a helpful resume assistant in a single ongoing chat about the user's CV.
			Return ONLY a JSON object with exactly these keys:
			{
			  "message": "natural reply to the user",
			  "proposed": null
			}

			Focus modes:
			- Scope "all": you see the FULL resume JSON. When proposing edits, "proposed" must be the FULL
			  revised resume in the same shape as the input document.
			- Scope is a section name (optionally with an index): you see only that block. When proposing edits,
			  "proposed" must be the FULL revised block value in the SAME JSON shape as the input.

			Critical rules for "proposed":
			- Default is null.
			- Set "proposed" ONLY if the user clearly asks you to change/rewrite/improve/update the section text,
			  OR you are offering a concrete revision they can apply.
			- Greetings, thanks, questions, opinions, explanations, brainstorming → proposed MUST be null.
			- If you are not changing the content, proposed MUST be null. Never echo the current JSON as proposed.

			Source-of-truth rules:
			- If a Career path biography is provided, treat it as the user's factual background.
			- Prefer facts from Career path (+ profile name/email) over inventing new employers, degrees, dates, or skills.
			- You may rephrase and select relevant facts, but do not invent history.
			- If Career path is missing, say so when the user asks you to generate content from it, and keep facts truthful.

			Vacancy targeting:
			- If a Target vacancy description is provided, tailor wording, emphasis, and keyword alignment toward that role.
			- Still do not invent experience that is not in Career path / resume — only reframe what is true.
			- When vacancy context is present and the user asks to improve content, prefer relevance to that vacancy.

			"message" rules:
			- Reply naturally in the user's language (e.g. Russian greeting → Russian reply).
			- Do not reply with only "OK".
			""";

	private static final int MAX_HISTORY_TURNS = 20;
	private static final int MAX_HISTORY_CHARS = 8_000;

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
		boolean wholeResume = SCOPE_ALL.equals(section);
		if (!wholeResume && !SECTIONS.contains(section)) {
			throw new IllegalArgumentException(
					"Unknown section '" + request.section() + "'. Use '" + SCOPE_ALL + "' or one of: " + SECTIONS
			);
		}

		ResumeDocument resume = resumeService.get();
		Integer itemIndex = wholeResume ? null : request.itemIndex();
		JsonNode current = wholeResume
				? objectMapper.valueToTree(resume)
				: sectionValue(resume, section, itemIndex);
		var profile = profileService.getProfile();
		String careerPath = profile.careerPath();
		boolean hasCareerPath = careerPath != null && !careerPath.isBlank();
		String vacancyContext = request.vacancyContext();
		boolean hasVacancy = vacancyContext != null && !vacancyContext.isBlank();

		String scope = wholeResume
				? SCOPE_ALL
				: (itemIndex == null ? section : section + "[" + itemIndex + "]");

		List<ChatTurn> history = capHistory(request.history());
		String model = resolveModel(request.model());
		log.info(
				"AI suggest section={} itemIndex={} model={} hasCareerPath={} hasVacancy={} historyTurns={}",
				scope,
				itemIndex,
				model,
				hasCareerPath,
				hasVacancy,
				history.size()
		);

		StringBuilder historyBlock = new StringBuilder();
		if (!history.isEmpty()) {
			historyBlock.append("Recent conversation (same ongoing chat):\n");
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
				Career path (PRIMARY factual biography — use this when drafting or improving content):
				%s
				""".formatted(careerPath.strip())
				: """
				Career path: (not filled in yet — do not invent a biography; work only with the resume JSON and user message)
				""";

		String vacancyBlock = hasVacancy
				? """
				Target vacancy (role the user is applying to — tailor emphasis and keywords toward this):
				%s
				""".formatted(vacancyContext.strip())
				: """
				Target vacancy: (not provided — keep content generally strong; do not invent a job posting)
				""";

		String focusBlock = wholeResume
				? """
				Focus: FULL resume. The JSON below is the entire CV document.
				When proposing edits, return the complete revised resume as "proposed".
				"""
				: """
				Focus: section "%s" only. The JSON below is just this block (the rest of the resume is out of scope for "proposed").
				When proposing edits, return only the revised block value as "proposed".
				""".formatted(scope);

		String userPrompt = """
				%s
				Current JSON:
				%s

				%s
				%s
				%s
				%sUser message:
				%s

				Remember: if this is just chat (hello, question, feedback) set proposed to null.
				When editing, ground new content in the Career path when it is available.
				When a Target vacancy is provided, align wording with that role without inventing facts.
				""".formatted(
				focusBlock,
				objectMapper.writeValueAsString(current),
				profileBlock,
				careerBlock,
				vacancyBlock,
				historyBlock,
				request.instruction().strip()
		);

		// The client has already logged failure details for the operator; users get a plain message.
		String content = switch (openRouterClient.complete(SYSTEM, userPrompt, model)) {
			case ChatResult.Answer(String answer) -> answer;
			case ChatResult.Busy(Duration retryAfter) -> throw new ServiceUnavailableException(
					"The AI is busy right now. Try again in " + retryAfter.toSeconds() + " seconds.", retryAfter);
			case ChatResult.Rejected _ -> throw new IllegalArgumentException(
					"The AI could not handle a change this big. Try a shorter message or one section at a time.");
			case ChatResult.Misconfigured _ -> throw new ServiceUnavailableException(
					"The AI is not available right now. Please try again later.", null);
			case ChatResult.Unavailable _ -> throw new ServiceUnavailableException(
					"The AI did not answer. Please try again.", null);
		};
		JsonNode parsed = objectMapper.readTree(content);
		String message = parsed.path("message").asString("").strip();
		if (message.isBlank()) {
			message = "…";
		}

		JsonNode proposed = parsed.get("proposed");
		// Drop no-op proposals (model sometimes echoes the current JSON).
		if (proposed == null || proposed.isNull() || proposed.isMissingNode() || proposed.equals(current)) {
			proposed = null;
		}

		log.info("AI suggest done section={} hasProposed={}", scope, proposed != null);
		return new SuggestResponse(wholeResume ? SCOPE_ALL : section, itemIndex, message, proposed);
	}

	/**
	 * Keeps the newest turns only: at most {@link #MAX_HISTORY_TURNS}, then drop oldest
	 * until total role+content chars ≤ {@link #MAX_HISTORY_CHARS}.
	 */
	private static List<ChatTurn> capHistory(List<ChatTurn> history) {
		if (history == null || history.isEmpty()) {
			return List.of();
		}
		List<ChatTurn> turns = history.stream()
				.filter(t -> t != null && t.role() != null && t.content() != null)
				.toList();
		if (turns.size() > MAX_HISTORY_TURNS) {
			turns = turns.subList(turns.size() - MAX_HISTORY_TURNS, turns.size());
		}
		int total = 0;
		for (ChatTurn turn : turns) {
			total += turn.role().length() + turn.content().length();
		}
		int from = 0;
		while (from < turns.size() && total > MAX_HISTORY_CHARS) {
			ChatTurn drop = turns.get(from);
			total -= drop.role().length() + drop.content().length();
			from++;
		}
		return from == 0 ? List.copyOf(turns) : List.copyOf(turns.subList(from, turns.size()));
	}

	private String resolveModel(String requested) {
		if (requested == null || requested.isBlank()) {
			return null; // OpenRouterClient uses configured default
		}
		String model = requested.strip();
		if (!ALLOWED_MODELS.contains(model)) {
			throw new IllegalArgumentException(
					"Unsupported model '" + model + "'. Allowed: " + ALLOWED_MODELS
			);
		}
		return model;
	}

	private JsonNode sectionValue(ResumeDocument resume, String section, Integer itemIndex) {
		Object value = switch (section) {
			case "header" -> Map.of(
					"name", resume.name(),
					"headline", resume.headline(),
					"phone", resume.phone(),
					"email", resume.email(),
					"linkedinUrl", resume.linkedinUrl(),
					"linkedinLabel", resume.linkedinLabel()
			);
			case "profile" -> resume.profile();
			case "experience" -> resume.experience();
			case "education" -> resume.education();
			case "achievements" -> resume.achievements();
			case "skills" -> resume.skills();
			default -> throw new IllegalArgumentException("Unknown section: " + section);
		};
		if (itemIndex != null) {
			if (!(value instanceof List<?> list)) {
				throw new IllegalArgumentException(
						"itemIndex is only supported for: experience, education, achievements, skills"
				);
			}
			if (itemIndex < 0 || itemIndex >= list.size()) {
				throw new IllegalArgumentException("itemIndex out of range for " + section + ": " + itemIndex);
			}
			value = list.get(itemIndex);
		}
		return objectMapper.valueToTree(value);
	}

	private static String blankToNone(String value) {
		return value == null || value.isBlank() ? "(none)" : value.strip();
	}
}
