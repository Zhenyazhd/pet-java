package com.jobsearch.atsworker;

import java.time.Duration;
import java.util.Map;

/** Worker settings, read from environment variables (see README.md). */
record WorkerConfig(
		int port,
		String token,
		int concurrency,
		Duration checkTimeout,
		int maxRequestBytes,
		int maxPages,
		Duration shutdownGrace,
		LlmConfig llm
) {

	/** A record prints all its fields; the token must never end up in a log line by accident. */
	@Override
	public String toString() {
		return "WorkerConfig[port=" + port + ", concurrency=" + concurrency + ", maxPages=" + maxPages + ", llm=" + llm + "]";
	}

	static WorkerConfig fromEnv(Map<String, String> env) {
		String token = env.getOrDefault("WORKER_TOKEN", "").strip();
		if (token.isEmpty()) {
			throw new IllegalArgumentException("WORKER_TOKEN is required");
		}
		return new WorkerConfig(
				positiveInt(env, "WORKER_PORT", 8091),
				token,
				positiveInt(env, "WORKER_CONCURRENCY", Runtime.getRuntime().availableProcessors()),
				Duration.ofSeconds(positiveInt(env, "CHECK_TIMEOUT_SECONDS", 20)),
				positiveInt(env, "MAX_REQUEST_BYTES", 16 * 1024 * 1024),
				positiveInt(env, "MAX_PAGES", 10),
				Duration.ofSeconds(positiveInt(env, "SHUTDOWN_GRACE_SECONDS", 25)),
				LlmConfig.fromEnv(env)
		);
	}

	static int positiveInt(Map<String, String> env, String name, int defaultValue) {
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
