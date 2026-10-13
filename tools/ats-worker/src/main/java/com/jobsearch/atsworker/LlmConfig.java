package com.jobsearch.atsworker;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/** Settings of the optional LLM scoring ({@code POST /score}); without an API key that endpoint answers 503. */
record LlmConfig(
		String apiKey,
		String baseUrl,
		String model,
		Duration timeout,
		int concurrency,
		int maxTokens
) {

	boolean configured() {
		return !apiKey.isBlank();
	}

	/** A record prints all its fields; the key must never end up in a log line by accident. */
	@Override
	public String toString() {
		return "LlmConfig[baseUrl=" + baseUrl + ", model=" + model + ", configured=" + configured() + "]";
	}

	static LlmConfig fromEnv(Map<String, String> env) {
		return new LlmConfig(
				env.getOrDefault("OPENROUTER_API_KEY", "").strip(),
				checkedBaseUrl(valueOrDefault(env, "OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1").replaceAll("/+$", "")),
				valueOrDefault(env, "SCORE_MODEL", "openai/gpt-4o-mini"),
				Duration.ofSeconds(WorkerConfig.positiveInt(env, "SCORE_TIMEOUT_SECONDS", 90)),
				WorkerConfig.positiveInt(env, "SCORE_CONCURRENCY", 4),
				WorkerConfig.positiveInt(env, "SCORE_MAX_TOKENS", 6000)
		);
	}

	/** Compose passes unset variables as empty strings; an empty value means "use the default". */
	private static String valueOrDefault(Map<String, String> env, String name, String defaultValue) {
		String value = env.getOrDefault(name, "").strip();
		return value.isEmpty() ? defaultValue : value;
	}

	/** The API key travels to this address, so it must be https (a local test server may use http). */
	private static String checkedBaseUrl(String baseUrl) {
		URI uri;
		try {
			uri = URI.create(baseUrl);
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("OPENROUTER_BASE_URL is not a valid URL: " + baseUrl);
		}
		boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
		if (uri.getHost() == null || !("https".equals(uri.getScheme()) || (local && "http".equals(uri.getScheme())))) {
			throw new IllegalArgumentException("OPENROUTER_BASE_URL must be an https URL, got " + baseUrl);
		}
		return baseUrl;
	}
}
