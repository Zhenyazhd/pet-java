package com.jobsearch.core_api.ai;

import com.jobsearch.core_api.ai.OpenRouterConfig.ChatMessage;
import com.jobsearch.core_api.ai.OpenRouterConfig.ChatRequest;
import com.jobsearch.core_api.config.AppProperties;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Thin HTTP client for OpenRouter Chat Completions (OpenAI-compatible). */
@Component
public class OpenRouterClient {

	private static final Logger log = LoggerFactory.getLogger(OpenRouterClient.class);

	private final RestClient restClient;
	private final AppProperties appProperties;
	private final ObjectMapper objectMapper;

	public OpenRouterClient(
			@Qualifier("openRouterRestClient") RestClient restClient,
			AppProperties appProperties,
			ObjectMapper objectMapper
	) {
		this.restClient = restClient;
		this.appProperties = appProperties;
		this.objectMapper = objectMapper;
	}

	/**
	 * Sends system+user prompts and returns the assistant message content.
	 * Expects JSON-mode output from the model.
	 *
	 * @param modelOverride OpenRouter model id, or null/blank to use configured default
	 */
	public String chat(String systemPrompt, String userPrompt, String modelOverride) {
		AppProperties.OpenRouter cfg = appProperties.getOpenRouter();
		if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
			throw new IllegalStateException(
					"OPENROUTER_API_KEY is not set. Add it to your environment or .env and restart the API."
			);
		}

		String model = (modelOverride != null && !modelOverride.isBlank())
				? modelOverride.strip()
				: cfg.getModel();

		log.info("OpenRouter chat request model={} userPromptChars={}", model, userPrompt.length());

		ChatRequest body = new ChatRequest(
				model,
				List.of(
						new ChatMessage("system", systemPrompt),
						new ChatMessage("user", userPrompt)
				),
				Map.of("type", "json_object")
		);

		String raw;
		try {
			raw = restClient.post()
					.uri("/chat/completions")
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(String.class);
		}
		catch (RestClientResponseException ex) {
			log.error("OpenRouter HTTP {} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
			throw new IllegalStateException(
					"OpenRouter error " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString(),
					ex
			);
		}

		if (raw == null || raw.isBlank()) {
			throw new IllegalStateException("OpenRouter returned an empty response");
		}

		JsonNode root = objectMapper.readTree(raw);
		String content = root.path("choices").path(0).path("message").path("content").asString("");
		if (content.isBlank()) {
			log.error("OpenRouter response missing content: {}", raw);
			throw new IllegalStateException("OpenRouter response missing message content: " + raw);
		}
		log.debug("OpenRouter response chars={}", content.length());
		return stripCodeFences(content);
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
}
