package com.jobsearch.joblist.rank;

import com.jobsearch.joblist.common.HttpErrors;
import com.jobsearch.joblist.config.JobListProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/** Chat completions through OpenRouter. */
@Component
public class LlmClient {

	private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
	private static final int MAX_SCANNED_CHARS = 100_000;
	private static final int MAX_CANDIDATES = 50;

	private final RestClient restClient;
	private final JobListProperties.Llm settings;

	public LlmClient(@Qualifier("openRouterRestClient") RestClient restClient, JobListProperties properties) {
		this.restClient = restClient;
		this.settings = properties.llm();
	}

	/**
	 * What one call came back with. {@code costKnown} is false when the provider did not say what it charged (no
	 * {@code usage.cost}) or the call may have been billed without an answer (a timeout, a 5xx): the caller must then
	 * count the worst case, not zero. {@code cutOff} marks an answer that hit {@code max_tokens}: it has no usable content
	 * but is not a failure of the call.
	 */
	public record Completion(String content, BigDecimal costUsd, boolean costKnown, String error, boolean cutOff) {

		/** A finished call with a known cost; what most callers and the tests need. */
		public Completion(String content, BigDecimal costUsd, String error) {
			this(content, costUsd, true, error, false);
		}

		static Completion failed(String error, boolean costKnown) {
			return new Completion(null, BigDecimal.ZERO, costKnown, error, false);
		}
	}

	/** @param maxTokens the longest answer the call may produce: it bounds what one call can cost */
	public Completion complete(String systemPrompt, String userPrompt, int maxTokens) {
		Map<String, Object> body = Map.of(
				"model", settings.model(),
				"messages", List.of(Map.of("role", "system", "content", systemPrompt), Map.of("role", "user", "content", userPrompt)),
				"response_format", Map.of("type", "json_object"),
				"temperature", 0,
				"max_tokens", maxTokens,
				"usage", Map.of("include", true));
		JsonNode response;
		try {
			response = restClient.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON)
					.body(body).retrieve().body(JsonNode.class);
		}
		catch (RestClientResponseException ex) {
			log.warn("Model call failed model={} error={}", settings.model(), HttpErrors.describe(ex));
			// A refusal (4xx) happened before anything was generated, so it cost nothing; a 5xx or a 408 may have been billed.
			int status = ex.getStatusCode().value();
			return Completion.failed(explain(status), ex.getStatusCode().is4xxClientError() && status != 408);
		}
		catch (RestClientException ex) {
			log.warn("Model call failed model={} error={}", settings.model(), HttpErrors.describe(ex));
			return Completion.failed("The model could not be reached", false);
		}
		if (response == null) {
			return Completion.failed("The model returned an empty response", false);
		}
		JsonNode choice = response.path("choices").path(0);
		JsonNode reportedCost = response.path("usage").path("cost");
		boolean costKnown = reportedCost.isNumber();
		BigDecimal cost = costKnown ? reportedCost.decimalValue() : BigDecimal.ZERO;
		log.info("Model answered model={} promptTokens={} completionTokens={} costUsd={}", settings.model(),
				response.path("usage").path("prompt_tokens").asInt(0), response.path("usage").path("completion_tokens").asInt(0), cost);
		if ("length".equals(choice.path("finish_reason").asString(""))) {
			return new Completion(null, cost, costKnown, null, true);
		}
		String content = choice.path("message").path("content").asString("");
		return content.isBlank() ? new Completion(null, cost, costKnown, "The model returned no content", false)
				: new Completion(extractJson(content), cost, costKnown, null, false);
	}

	/** What the caller is told about a refusal: the cause that an operator can act on, nothing from the provider's body. */
	private static String explain(int status) {
		return switch (status) {
			case 401, 403 -> "The model provider refused the API key";
			case 402 -> "The model provider account has no credit";
			case 404 -> "The model provider does not know the configured model";
			case 429 -> "The model provider is rate limiting the requests";
			default -> "The model could not be reached";
		};
	}

	/**
	 * Models sometimes put the object in code fences or write a sentence around it despite JSON mode. Every balanced
	 * {...} in the text is a candidate (braces inside strings do not count); the first one that holds a "results" key is
	 * the answer, so a brace in the prose before or after it does not matter. With no such object the first candidate is
	 * returned, and anything else as it is; both fail to parse later.
	 */
	static String extractJson(String content) {
		String trimmed = content.strip();
		// A real answer is a few kilobytes (max_tokens). The scan is bounded, so a provider that ignores max_tokens, or
		// a text made of thousands of '{', cannot keep the worker busy for minutes.
		if (trimmed.length() > MAX_SCANNED_CHARS) {
			trimmed = trimmed.substring(0, MAX_SCANNED_CHARS);
		}
		String first = null;
		int tried = 0;
		for (int start = trimmed.indexOf('{'); start >= 0 && tried++ < MAX_CANDIDATES; start = trimmed.indexOf('{', start + 1)) {
			int end = closingBrace(trimmed, start);
			if (end < 0) {
				continue;
			}
			String candidate = trimmed.substring(start, end + 1);
			if (candidate.contains("\"results\"")) {
				return candidate;
			}
			first = first == null ? candidate : first;
		}
		return first != null ? first : trimmed;
	}

	/** The index of the '}' that closes the '{' at {@code start}, or -1. Linear; a string's braces and escapes are skipped. */
	private static int closingBrace(String text, int start) {
		int depth = 0;
		boolean inString = false;
		for (int i = start; i < text.length(); i++) {
			char c = text.charAt(i);
			if (inString) {
				if (c == '\\') {
					i++;
				}
				else if (c == '"') {
					inString = false;
				}
			}
			else if (c == '"') {
				inString = true;
			}
			else if (c == '{') {
				depth++;
			}
			else if (c == '}' && --depth == 0) {
				return i;
			}
		}
		return -1;
	}
}
