package com.jobsearch.latexworker;

import java.time.Duration;
import java.util.Map;

/** Worker settings, read from environment variables (see SPEC.md §3.4). */
record WorkerConfig(
		int port,
		String token,
		int concurrency,
		Duration compileTimeout,
		int maxSourceBytes,
		int maxPdfBytes,
		String tectonicBin
) {

	static WorkerConfig fromEnv(Map<String, String> env) {
		String token = env.getOrDefault("WORKER_TOKEN", "").strip();
		if (token.isEmpty()) {
			throw new IllegalArgumentException("WORKER_TOKEN is required");
		}
		return new WorkerConfig(
				positiveInt(env, "WORKER_PORT", 8090),
				token,
				positiveInt(env, "WORKER_CONCURRENCY", Runtime.getRuntime().availableProcessors()),
				Duration.ofSeconds(positiveInt(env, "COMPILE_TIMEOUT_SECONDS", 60)),
				positiveInt(env, "MAX_SOURCE_BYTES", 1024 * 1024),
				positiveInt(env, "MAX_PDF_BYTES", 10 * 1024 * 1024),
				env.getOrDefault("TECTONIC_BIN", "tectonic")
		);
	}

	private static int positiveInt(Map<String, String> env, String name, int defaultValue) {
		String raw = env.get(name);
		if (raw == null || raw.isBlank()) {
			return defaultValue;
		}
		int value;
		try {
			value = Integer.parseInt(raw.strip());
		}
		catch (NumberFormatException ex) {
			throw new IllegalArgumentException(name + " must be a number, got '" + raw + "'");
		}
		if (value <= 0) {
			throw new IllegalArgumentException(name + " must be positive, got " + value);
		}
		return value;
	}
}
