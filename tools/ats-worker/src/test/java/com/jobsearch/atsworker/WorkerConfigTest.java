package com.jobsearch.atsworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkerConfigTest {

	@Test
	void theTokenIsRequired() {
		assertThrows(IllegalArgumentException.class, () -> WorkerConfig.fromEnv(Map.of()));
		assertThrows(IllegalArgumentException.class, () -> WorkerConfig.fromEnv(Map.of("WORKER_TOKEN", "  ")));
	}

	@Test
	void numbersMustBePositive() {
		assertThrows(IllegalArgumentException.class,
				() -> WorkerConfig.fromEnv(Map.of("WORKER_TOKEN", "t", "MAX_PAGES", "0")));
		assertThrows(IllegalArgumentException.class,
				() -> WorkerConfig.fromEnv(Map.of("WORKER_TOKEN", "t", "WORKER_PORT", "eighty")));
	}

	@Test
	void secretsDoNotAppearInTheTextFormOfTheConfig() {
		WorkerConfig config = WorkerConfig.fromEnv(Map.of("WORKER_TOKEN", "secret-token", "OPENROUTER_API_KEY", "secret-key"));

		assertFalse(config.toString().contains("secret-token"), config.toString());
		assertFalse(config.toString().contains("secret-key"), config.toString());
		assertTrue(config.llm().configured());
	}

	@Test
	void theKeyOnlyGoesToAnHttpsAddressOrALocalOne() {
		assertEquals("https://openrouter.ai/api/v1", LlmConfig.fromEnv(Map.of()).baseUrl());
		assertEquals("http://localhost:9999", LlmConfig.fromEnv(Map.of("OPENROUTER_BASE_URL", "http://localhost:9999/")).baseUrl());
		assertThrows(IllegalArgumentException.class,
				() -> LlmConfig.fromEnv(Map.of("OPENROUTER_BASE_URL", "http://openrouter.ai/api/v1")));
		assertThrows(IllegalArgumentException.class, () -> LlmConfig.fromEnv(Map.of("OPENROUTER_BASE_URL", "not a url")));
	}

	@Test
	void anEmptyValueFromComposeMeansTheDefault() {
		LlmConfig config = LlmConfig.fromEnv(Map.of("SCORE_MODEL", "", "OPENROUTER_BASE_URL", " ", "OPENROUTER_API_KEY", ""));

		assertEquals("openai/gpt-4o-mini", config.model());
		assertEquals("https://openrouter.ai/api/v1", config.baseUrl());
		assertFalse(config.configured());
	}
}
