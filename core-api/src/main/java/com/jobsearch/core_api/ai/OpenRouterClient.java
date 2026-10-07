package com.jobsearch.core_api.ai;

import com.jobsearch.core_api.common.Strings;
import com.jobsearch.core_api.config.AppProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Thin HTTP client for OpenRouter Chat Completions (OpenAI-compatible). */
@Component
public class OpenRouterClient {

	private static final Logger log = LoggerFactory.getLogger(OpenRouterClient.class);
	/** OpenRouter rate-limited us without saying for how long. */
	private static final Duration UPSTREAM_RETRY_AFTER_DEFAULT = Duration.ofSeconds(10);
	/** All local slots are taken; calls take seconds, so one should free up soon. */
	private static final Duration LOCAL_RETRY_AFTER = Duration.ofSeconds(5);

	private final RestClient restClient;
	private final AppProperties appProperties;
	private final ObjectMapper objectMapper;
	private final Semaphore slots;

	public OpenRouterClient(
			@Qualifier("openRouterRestClient") RestClient restClient,
			AppProperties appProperties,
			ObjectMapper objectMapper
	) {
		this.restClient = restClient;
		this.appProperties = appProperties;
		this.objectMapper = objectMapper;
		this.slots = new Semaphore(appProperties.getOpenRouter().getMaxConcurrentRequests());
	}

	/**
	 * Sends system+user prompts and returns the assistant's JSON-mode answer, or why there is none.
	 * At most {@code max-concurrent-requests} calls run at once per instance; beyond that the answer is
	 * {@link ChatResult.Busy} immediately rather than a wait in memory.
	 *
	 * @param modelOverride OpenRouter model id, or null to use configured default
	 */
	public ChatResult complete(String systemPrompt, String userPrompt, String modelOverride) {
		if (!hasApiKey()) {
			log.error("OPENROUTER_API_KEY is not set");
			return new ChatResult.Misconfigured("OPENROUTER_API_KEY is not set");
		}
		if (!slots.tryAcquire()) {
			log.warn("OpenRouter call rejected: {} calls already in flight", appProperties.getOpenRouter().getMaxConcurrentRequests());
			return new ChatResult.Busy(LOCAL_RETRY_AFTER);
		}
		try {
			return send(systemPrompt, userPrompt, modelOverride);
		}
		finally {
			slots.release();
		}
	}

	/**
	 * Older contract: throws {@link IllegalStateException} for every failure, keeping OpenRouter's HTTP
	 * error as the cause. Not counted against {@code max-concurrent-requests}. Answers are capped at
	 * {@code max-tokens} like everywhere else, but a cut-off answer is returned as-is. Kept for the vacancy
	 * import until it moves to {@link #complete}.
	 */
	public String chat(String systemPrompt, String userPrompt, String modelOverride) {
		if (!hasApiKey()) {
			log.error("OPENROUTER_API_KEY is not set");
			throw new IllegalStateException("AI service is not configured on this server");
		}
		String raw;
		try {
			raw = post(request(systemPrompt, userPrompt, modelOverride));
		}
		catch (RestClientResponseException ex) {
			String upstreamBody = ex.getResponseBodyAsString();
			log.error("OpenRouter HTTP {} body={}", ex.getStatusCode().value(), upstreamBody);
			throw new IllegalStateException(
					"OpenRouter error " + ex.getStatusCode().value() + ": " + Strings.abbreviate(upstreamBody, 400),
					ex
			);
		}
		if (raw == null || raw.isBlank()) {
			throw new IllegalStateException("OpenRouter returned an empty response");
		}
		String content = parse(raw).content();
		if (content == null) {
			log.error("OpenRouter response missing content: {}", Strings.abbreviate(raw, 400));
			throw new IllegalStateException("OpenRouter response missing message content: " + Strings.abbreviate(raw, 400));
		}
		return content;
	}

	private ChatResult send(String systemPrompt, String userPrompt, String modelOverride) {
		String raw;
		try {
			raw = post(request(systemPrompt, userPrompt, modelOverride));
		}
		catch (RestClientResponseException ex) {
			int status = ex.getStatusCode().value();
			log.error("OpenRouter HTTP {} body={}", status, Strings.abbreviate(ex.getResponseBodyAsString(), 400));
			String reason = "OpenRouter HTTP " + status;
			return switch (status) {
				case 429 -> new ChatResult.Busy(retryAfter(ex.getResponseHeaders()));
				case 400, 413 -> new ChatResult.Rejected(reason);
				case 401, 402, 403 -> new ChatResult.Misconfigured(reason);
				default -> new ChatResult.Unavailable(reason);
			};
		}
		catch (RestClientException ex) {
			// Connect/read timeouts and network errors.
			log.error("OpenRouter call failed: {}", ex.toString());
			return new ChatResult.Unavailable(ex.toString());
		}

		Completion completion = parseOrNull(raw);
		if (completion == null || completion.content() == null) {
			log.error("OpenRouter answer has no message content: {}", Strings.abbreviate(raw, 400));
			return new ChatResult.Unavailable("OpenRouter answer has no message content");
		}
		if (completion.cutOff()) {
			// The JSON is incomplete, and the same request would be cut off again.
			log.warn("OpenRouter answer cut off at max_tokens={}", appProperties.getOpenRouter().getMaxTokens());
			return new ChatResult.Rejected("answer cut off at max_tokens");
		}
		return new ChatResult.Answer(completion.content());
	}

	private ChatRequest request(String systemPrompt, String userPrompt, String modelOverride) {
		String model = modelOverride != null ? modelOverride : appProperties.getOpenRouter().getModel();
		return new ChatRequest(
				model,
				List.of(
						new ChatMessage("system", systemPrompt),
						new ChatMessage("user", userPrompt)
				),
				Map.of("type", "json_object"),
				appProperties.getOpenRouter().getMaxTokens()
		);
	}

	private String post(ChatRequest body) {
		log.info("OpenRouter chat request model={} userPromptChars={}", body.model(), body.messages().getLast().content().length());
		return restClient.post()
				.uri("/chat/completions")
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(String.class);
	}

	/** Reads a non-blank answer body and logs its token usage; throws if it is not JSON. */
	private Completion parse(String raw) {
		JsonNode root = objectMapper.readTree(raw);
		logUsage(root);
		JsonNode choice = root.path("choices").path(0);
		String content = choice.path("message").path("content").asString("");
		return new Completion(
				content.isBlank() ? null : stripCodeFences(content),
				"length".equals(choice.path("finish_reason").asString(""))
		);
	}

	/** Like {@link #parse}, but an empty or unreadable answer gives null. */
	private Completion parseOrNull(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return parse(raw);
		}
		catch (JacksonException ex) {
			return null;
		}
	}

	/** One line per answer, so spend can be traced per model; {@code model} is the one that actually answered. */
	private static void logUsage(JsonNode root) {
		JsonNode usage = root.path("usage");
		if (usage.isMissingNode()) {
			return;
		}
		log.info("OpenRouter usage model={} promptTokens={} completionTokens={} totalTokens={}",
				root.path("model").asString(""),
				usage.path("prompt_tokens").asInt(0),
				usage.path("completion_tokens").asInt(0),
				usage.path("total_tokens").asInt(0));
	}

	private boolean hasApiKey() {
		String apiKey = appProperties.getOpenRouter().getApiKey();
		return apiKey != null && !apiKey.isBlank();
	}

	private static Duration retryAfter(HttpHeaders headers) {
		String header = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
		if (header == null) {
			return UPSTREAM_RETRY_AFTER_DEFAULT;
		}
		try {
			return Duration.ofSeconds(Math.max(1, Long.parseLong(header.strip())));
		}
		catch (NumberFormatException ex) {
			return UPSTREAM_RETRY_AFTER_DEFAULT;
		}
	}

	/** Models sometimes wrap JSON in ``` fences despite json_object mode. */
	private static String stripCodeFences(String content) {
		String trimmed = content.strip();
		if (!trimmed.startsWith("```")) {
			return trimmed;
		}
		int firstNl = trimmed.indexOf('\n');
		int lastFence = trimmed.lastIndexOf("```");
		if (firstNl > 0 && lastFence > firstNl) {
			return trimmed.substring(firstNl + 1, lastFence).strip();
		}
		return trimmed;
	}

	record ChatMessage(String role, String content) {
	}

	record ChatRequest(
			String model,
			List<ChatMessage> messages,
			Map<String, Object> response_format,
			int max_tokens
	) {
	}

	/** {@code content} is null when the answer has none; {@code cutOff} when it stopped at max_tokens. */
	private record Completion(String content, boolean cutOff) {
	}
}
