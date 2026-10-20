package com.jobsearch.core_api.ai;

import java.util.Map;

/**
 * Builds the resume chat's system prompt: fixed text (the JSON contract, translation, page fit, the shape of
 * "proposed", the data-not-instructions rule) with the editable {@link PromptPart}s in between. Custom text never
 * touches the fixed pieces, so it cannot remove them. It can still contradict them; the fixed tail ends by restating
 * that the whole reply is the one JSON object.
 */
public final class SystemPromptComposer {

	private static final String HEAD = PromptPart.readResource("prompts/fixed/head.txt");
	private static final String MIDDLE = PromptPart.readResource("prompts/fixed/middle.txt");
	private static final String TAIL = PromptPart.readResource("prompts/fixed/tail.txt");
	private static final String GAP = "\n\n";

	private SystemPromptComposer() {
	}

	/** The prompt with the given custom texts in place of the defaults of their parts; parts not in the map keep the default. */
	public static String compose(Map<PromptPart, String> custom) {
		return HEAD
				+ part(PromptPart.COMMITMENTS, custom)
				+ MIDDLE
				+ part(PromptPart.TRUTHFULNESS, custom)
				+ part(PromptPart.VACANCY, custom)
				+ part(PromptPart.TAILORING, custom)
				+ TAIL;
	}

	public static String defaultPrompt() {
		return compose(Map.of());
	}

	private static String part(PromptPart part, Map<PromptPart, String> custom) {
		String text = custom.get(part);
		return (text == null ? part.defaultText() : text.strip()) + GAP;
	}
}
