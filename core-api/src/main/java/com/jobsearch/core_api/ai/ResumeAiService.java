package com.jobsearch.core_api.ai;

import com.jobsearch.core_api.common.BadRequestException;
import com.jobsearch.core_api.compile.PageLayout;
import com.jobsearch.core_api.compile.ResumeLayoutService;
import com.jobsearch.core_api.ai.AiDtos.ChatTurn;
import com.jobsearch.core_api.ai.AiDtos.SuggestRequest;
import com.jobsearch.core_api.ai.AiDtos.SuggestResponse;
import com.jobsearch.core_api.common.ServiceUnavailableException;
import com.jobsearch.core_api.profile.ProfileService;
import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import com.jobsearch.core_api.resume.ResumeService;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

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

	/** Extra model calls to bring a whole-CV proposal to one page; each costs a call and a compile. */
	private static final int MAX_FIT_ROUNDS = 2;
	/** No correction round starts after this long, so the whole answer stays well under a proxy's timeout. */
	private static final Duration FIT_START_LIMIT = Duration.ofSeconds(60);

	private static final int MAX_HISTORY_TURNS = 20;
	/**
	 * Roomy enough to keep the stage-1 analysis of a vacancy (a few thousand characters) together with the answers
	 * that follow it, which the rewrite of stage 2 is built on; the oldest turns are dropped first beyond this.
	 */
	private static final int MAX_HISTORY_CHARS = 30_000;
	/** One long turn (a pasted posting, a very long answer) must not push the analysis out on its own. */
	private static final int MAX_TURN_CHARS = 12_000;

	private final OpenRouterClient openRouterClient;
	private final ResumeService resumeService;
	private final ProfileService profileService;
	private final UserPromptService userPromptService;
	private final ObjectMapper objectMapper;
	private final ProposedValidator proposedValidator;
	private final ResumeLayoutService layoutService;

	public ResumeAiService(
			OpenRouterClient openRouterClient,
			ResumeService resumeService,
			ProfileService profileService,
			UserPromptService userPromptService,
			ObjectMapper objectMapper,
			ProposedValidator proposedValidator,
			ResumeLayoutService layoutService
	) {
		this.openRouterClient = openRouterClient;
		this.resumeService = resumeService;
		this.profileService = profileService;
		this.userPromptService = userPromptService;
		this.objectMapper = objectMapper;
		this.proposedValidator = proposedValidator;
		this.layoutService = layoutService;
	}

	public SuggestResponse suggest(SuggestRequest request) {
		String section = request.section().trim().toLowerCase(Locale.ROOT);
		boolean wholeResume = SCOPE_ALL.equals(section);
		if (!wholeResume && !SECTIONS.contains(section)) {
			throw new BadRequestException(
					"Unknown section '" + request.section() + "'. Use '" + SCOPE_ALL + "' or one of: " + SECTIONS
			);
		}

		ResumeDocument resume = resumeService.get();
		Integer itemIndex = wholeResume ? null : request.itemIndex();
		JsonNode current = wholeResume
				? withoutLayout(objectMapper.valueToTree(resume))
				: sectionValue(resume, section, itemIndex);
		var profile = profileService.getProfile();
		String systemPrompt = SystemPromptComposer.compose(userPromptService.customTexts());
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
			StringBuilder turns = new StringBuilder();
			for (ChatTurn turn : history) {
				turns.append("- ").append(turn.role()).append(": ").append(turn.content()).append('\n');
			}
			historyBlock.append("Recent conversation (same ongoing chat):\n")
					.append(fence("conversation", turns.toString()))
					.append('\n');
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
				""".formatted(fence("career_path", careerPath.strip()))
				: """
				Career path: NOT FILLED IN. You have no verified facts about the user's experience.
				Do not write jobs, achievements, skills, dates or numbers. You may improve the wording of text the user already
				wrote in the current JSON or told you in this chat. For anything else, ask the user what they did, or tell
				them to fill in the Career path on their Profile page.
				""";

		String vacancyBlock = hasVacancy
				? """
				Target vacancy (role the user is applying to — tailor emphasis and keywords toward this):
				%s
				""".formatted(fence("vacancy", vacancyContext.strip()))
				: """
				Target vacancy: (not provided — keep content generally strong; do not invent a job posting or its requirements)
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
				Every fact in "proposed" must come from the Career path, this conversation or the user's own CV text.
				If a needed fact is missing, ask the user or send them to the Career path field on the Profile page.
				When a Target vacancy is provided, align wording with that role without inventing facts.
				""".formatted(
				focusBlock,
				fence("current_json", objectMapper.writeValueAsString(current)) + layoutBlock(wholeResume ? resume : null),
				profileBlock,
				careerBlock,
				vacancyBlock,
				historyBlock,
				fence("user_message", request.instruction().strip())
		);

		long startedAt = System.nanoTime();
		Answer answer = interpret(ask(systemPrompt, userPrompt, model), wholeResume, section, itemIndex, current);
		if (wholeResume && answer.proposed() != null && answer.fitPage()) {
			answer = fitToPage(answer, systemPrompt, userPrompt, model, current, startedAt);
		}

		String message = answer.message();
		if (answer.rejected()) {
			String note = "(I could not prepare a valid edit this time. Try rephrasing, or ask for one section at a time.)";
			message = message.isBlank() ? note : message + "\n\n" + note;
		}
		else if (message.isBlank()) {
			message = "…";
		}

		log.info("AI suggest done section={} hasProposed={} rejected={}", scope, answer.proposed() != null, answer.rejected());
		return new SuggestResponse(wholeResume ? SCOPE_ALL : section, itemIndex, message, answer.proposed(), answer.rejected());
	}

	/** One reading of a model answer: the reply, the checked proposal (null when none, unchanged or invalid) and the fit flag. */
	private record Answer(String message, JsonNode proposed, boolean rejected, boolean fitPage) {
	}

	// The client has already logged failure details for the operator; users get a plain message.
	private String ask(String systemPrompt, String userPrompt, String model) {
		return switch (openRouterClient.complete(systemPrompt, userPrompt, model)) {
			case ChatResult.Answer(String answer) -> answer;
			case ChatResult.Busy(Duration retryAfter) -> throw new ServiceUnavailableException(
					"The AI is busy right now. Try again in " + retryAfter.toSeconds() + " seconds.", retryAfter);
			case ChatResult.Rejected _ -> throw new BadRequestException(
					"The AI could not handle a change this big. Try a shorter message or one section at a time.");
			case ChatResult.Misconfigured _ -> throw new ServiceUnavailableException(
					"The AI is not available right now. Please try again later.", null);
			case ChatResult.Unavailable _ -> throw new ServiceUnavailableException(
					"The AI did not answer. Please try again.", null);
		};
	}

	private Answer interpret(String content, boolean wholeResume, String section, Integer itemIndex, JsonNode current) {
		JsonNode parsed;
		try {
			parsed = objectMapper.readTree(content);
		}
		catch (JacksonException ex) {
			log.warn("AI answer is not valid JSON chars={}", content.length());
			throw new ServiceUnavailableException("The AI did not answer. Please try again.", null);
		}
		String message = parsed.path("message").asString("").strip();
		// Only an explicit false exempts a proposal from the page fit.
		boolean fitPage = !parsed.path("fit_page").isBoolean() || parsed.path("fit_page").asBoolean();

		JsonNode proposed = parsed.get("proposed");
		boolean rejected = false;
		if (proposed != null && !proposed.isNull() && !proposed.isMissingNode()) {
			if (wholeResume && proposed.isObject()) {
				proposed = keepMissingFields(proposed, current);
			}
			JsonNode checked = proposedValidator.check(wholeResume ? SCOPE_ALL : section, itemIndex, proposed);
			if (checked == null) {
				rejected = true;
				proposed = null;
			}
			else {
				// Compared in the checked shape: the model often echoes the current text wrapped, reordered or without
				// the keys it did not touch, and that is not a change.
				proposed = changes(checked, current, section) ? checked : null;
			}
		}
		else {
			proposed = null;
		}
		return new Answer(message, proposed, rejected, fitPage);
	}

	/**
	 * Measures the proposal on the page and, while it is the wrong length, asks the model to correct it (at most
	 * {@link #MAX_FIT_ROUNDS} times). Returns the best candidate seen; any failure along the way keeps the best so far.
	 */
	private Answer fitToPage(Answer first, String systemPrompt, String userPrompt, String model, JsonNode current,
			long startedAt) {
		PageLayout firstLayout = measure(first.proposed());
		if (firstLayout == null) {
			return first;
		}
		Answer best = first;
		PageLayout bestLayout = firstLayout;
		for (int round = 1; round <= MAX_FIT_ROUNDS && !PageFit.fits(bestLayout); round++) {
			if (Duration.ofNanos(System.nanoTime() - startedAt).compareTo(FIT_START_LIMIT) > 0) {
				log.info("AI page fit round {} skipped: the answer is already slow", round);
				break;
			}
			Answer next;
			try {
				// Always corrects the best candidate so far, not a worse one the previous round produced.
				String correction = userPrompt + correctionBlock(best.proposed(), bestLayout);
				next = interpret(ask(systemPrompt, correction, model), true, SCOPE_ALL, null, current);
			}
			catch (RuntimeException ex) {
				log.warn("AI page fit round {} stopped: {}", round, ex.getClass().getSimpleName());
				break;
			}
			if (!next.fitPage() || next.proposed() == null || next.proposed().equals(best.proposed())) {
				break;
			}
			PageLayout layout = measure(next.proposed());
			if (layout == null) {
				break;
			}
			if (PageFit.distance(layout) < PageFit.distance(bestLayout)) {
				// The corrected reply only explains the page fit; the first one explained the edit itself.
				best = new Answer(first.message() + "\n\n" + next.message(), next.proposed(), false, true);
				bestLayout = layout;
			}
		}
		log.info("AI page fit pages={} lines={} fits={}", bestLayout.pages(), PageFit.lines(bestLayout), PageFit.fits(bestLayout));
		return best;
	}

	private PageLayout measure(JsonNode proposed) {
		try {
			return layoutService.of(objectMapper.treeToValue(proposed, ResumeDocument.class)).orElse(null);
		}
		catch (RuntimeException ex) {
			log.debug("Page fit skipped: {}", ex.getClass().getSimpleName());
			return null;
		}
	}

	private String layoutBlock(ResumeDocument resume) {
		if (resume == null) {
			return "";
		}
		return layoutService.of(resume)
				.map(layout -> "\n\nPage layout of the saved resume in <current_json> (measured on its compiled PDF):\n"
						+ PageFit.describe(layout))
				.orElse("");
	}

	private static String correctionBlock(JsonNode previous, PageLayout layout) {
		return """


				Correction: your previous "proposed" was measured on the page.
				%s
				Measured result:
				%s
				Return the full resume again, with the length corrected. "message" must say, in the language of the user's
				message, what you changed for the page fit. Never invent content to fill space; if the sources have nothing
				more to add, say so and return the same resume.
				""".formatted(fence("previous_proposal", previous.toString()), PageFit.describe(layout));
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
				.map(t -> t.content().length() <= MAX_TURN_CHARS
						? t
						: new ChatTurn(t.role(), t.content().substring(0, MAX_TURN_CHARS) + "…"))
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
			throw new BadRequestException(
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
			default -> throw new BadRequestException("Unknown section: " + section);
		};
		if (itemIndex != null) {
			if (!(value instanceof List<?> list)) {
				throw new BadRequestException(
						"itemIndex is only supported for: experience, education, achievements, skills"
				);
			}
			if (itemIndex < 0 || itemIndex >= list.size()) {
				throw new BadRequestException("itemIndex out of range for " + section + ": " + itemIndex);
			}
			value = list.get(itemIndex);
		}
		return objectMapper.valueToTree(value);
	}

	/** Every tag that marks a block in the prompt, in any spelling a model may still read as one (spaces, zero-width characters, case). */
	private static final Pattern FENCE_TAGS = Pattern.compile(
			"<[\\s\\p{Cf}]*/?[\\s\\p{Cf}]*(?:current_json|career_path|vacancy|conversation|user_message|previous_proposal)[\\s\\p{Cf}]*>",
			Pattern.CASE_INSENSITIVE);

	/**
	 * Wraps untrusted text in a tag the model is told to read as data. Any tag of ours inside the text, opening or
	 * closing, is removed: a pasted posting could otherwise end its block early, or open a fake {@code <user_message>}
	 * and have what follows read as the user's request.
	 */
	static String fence(String tag, String text) {
		// Repeat: removing one tag can join the pieces around it into a new one ("</vac</vacancy>ancy>").
		String safe = text;
		for (String previous = null; !safe.equals(previous); ) {
			previous = safe;
			safe = FENCE_TAGS.matcher(safe).replaceAll("");
		}
		return "<" + tag + ">\n" + safe + "\n</" + tag + ">";
	}

	/**
	 * A whole-resume answer that leaves a key out must not erase it: the validator reads a missing key as empty, which
	 * would wipe a list or the contact details the model simply did not repeat. An explicit null still means empty.
	 */
	private static JsonNode keepMissingFields(JsonNode proposed, JsonNode current) {
		ObjectNode merged = (ObjectNode) proposed.deepCopy();
		for (Map.Entry<String, JsonNode> field : current.properties()) {
			if (!merged.has(field.getKey())) {
				merged.set(field.getKey(), field.getValue());
			}
		}
		return merged;
	}

	private static boolean changes(JsonNode checked, JsonNode current, String section) {
		if (checked.isObject() && ("all".equals(section) || "header".equals(section))) {
			for (Map.Entry<String, JsonNode> field : checked.properties()) {
				if (!field.getValue().equals(current.get(field.getKey()))) {
					return true;
				}
			}
			return false;
		}
		return !checked.equals(current);
	}

	private static String blankToNone(String value) {
		return value == null || value.isBlank() ? "(none)" : value.strip();
	}

	/** The typesetting settings are the user's; the model works on the text only. */
	private static JsonNode withoutLayout(JsonNode resume) {
		if (resume instanceof ObjectNode object) {
			object.remove("layout");
		}
		return resume;
	}
}
