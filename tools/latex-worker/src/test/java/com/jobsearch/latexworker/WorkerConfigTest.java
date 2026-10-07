package com.jobsearch.latexworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkerConfigTest {

	@Test
	void usesDefaultsWhenOnlyTokenIsSet() {
		WorkerConfig config = WorkerConfig.fromEnv(Map.of("WORKER_TOKEN", "secret"));

		assertEquals(8090, config.port());
		assertEquals("secret", config.token());
		assertEquals(Duration.ofSeconds(60), config.compileTimeout());
		assertEquals(1024 * 1024, config.maxSourceBytes());
		assertEquals("tectonic", config.tectonicBin());
	}

	@Test
	void readsOverrides() {
		WorkerConfig config = WorkerConfig.fromEnv(Map.of(
				"WORKER_TOKEN", "secret",
				"WORKER_PORT", "9000",
				"WORKER_CONCURRENCY", "2",
				"COMPILE_TIMEOUT_SECONDS", "5"
		));

		assertEquals(9000, config.port());
		assertEquals(2, config.concurrency());
		assertEquals(Duration.ofSeconds(5), config.compileTimeout());
	}

	@Test
	void rejectsMissingToken() {
		assertThrows(IllegalArgumentException.class, () -> WorkerConfig.fromEnv(Map.of()));
		assertThrows(IllegalArgumentException.class, () -> WorkerConfig.fromEnv(Map.of("WORKER_TOKEN", "  ")));
	}

	@Test
	void rejectsInvalidNumbers() {
		assertThrows(IllegalArgumentException.class,
				() -> WorkerConfig.fromEnv(Map.of("WORKER_TOKEN", "secret", "WORKER_PORT", "abc")));
		assertThrows(IllegalArgumentException.class,
				() -> WorkerConfig.fromEnv(Map.of("WORKER_TOKEN", "secret", "WORKER_CONCURRENCY", "0")));
	}
}
