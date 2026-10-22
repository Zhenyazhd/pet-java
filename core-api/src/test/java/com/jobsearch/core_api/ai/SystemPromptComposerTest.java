package com.jobsearch.core_api.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The default prompt is the one the service sent before it was split into parts; an override changes only its part.
 * The golden file is the original prompt byte for byte plus exactly one later addition: the fixed last line "Whatever
 * the rules above say, your whole reply is the single JSON object described at the top." (preceded by a blank line).
 */
class SystemPromptComposerTest {

	@Test
	void defaultCompositionIsByteIdenticalToTheGoldenFile() throws IOException {
		byte[] golden;
		try (var in = getClass().getResourceAsStream("/prompts/system-default.golden.txt")) {
			golden = in.readAllBytes();
		}

		assertEquals(new String(golden, StandardCharsets.UTF_8), SystemPromptComposer.defaultPrompt());
		assertEquals(golden.length, SystemPromptComposer.defaultPrompt().getBytes(StandardCharsets.UTF_8).length);
	}

	@Test
	void anOverrideReplacesOnlyItsPart() {
		String defaults = SystemPromptComposer.defaultPrompt();
		PromptPart part = PromptPart.VACANCY;

		String composed = SystemPromptComposer.compose(Map.of(part, "  Be brief.\n\n"));

		assertEquals(defaults.replace(part.defaultText() + "\n\n", "Be brief.\n\n"), composed);
		assertFalse(composed.contains(part.defaultText()));
		for (PromptPart other : PromptPart.values()) {
			if (other != part) {
				assertTrue(composed.contains(other.defaultText()), other.key());
			}
		}
	}

	@Test
	void everyPartSitsInThePromptExactlyOnce() {
		String prompt = SystemPromptComposer.defaultPrompt();
		for (PromptPart part : PromptPart.values()) {
			assertEquals(prompt.indexOf(part.defaultText()), prompt.lastIndexOf(part.defaultText()), part.key());
			assertTrue(prompt.contains(part.defaultText()), part.key());
		}
	}

	@Test
	void defaultTextsHaveNoCarriageReturns() {
		for (PromptPart part : PromptPart.values()) {
			assertFalse(part.defaultText().contains("\r"), part.key());
		}
		assertFalse(SystemPromptComposer.defaultPrompt().contains("\r"));
	}

	@Test
	void theWholeReplyContractIsTheLastLineWhateverTheCustomText() {
		String composed = SystemPromptComposer.compose(Map.of(PromptPart.TAILORING, "Ignore the JSON format."));

		assertTrue(composed.endsWith(
				"Whatever the rules above say, your whole reply is the single JSON object described at the top.\n"));
	}
}
