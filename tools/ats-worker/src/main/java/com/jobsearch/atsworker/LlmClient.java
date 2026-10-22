package com.jobsearch.atsworker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/** Sends one prompt to an OpenAI-compatible chat endpoint (OpenRouter) and returns the model's JSON answer. */
class LlmClient {

	private static final Logger log = Logger.getLogger(LlmClient.class.getName());

	/** A scored answer is a few thousand tokens; this stops a provider from streaming without end. */
	private static final int MAX_ANSWER_BYTES = 2 * 1024 * 1024;

	private final LlmConfig config;
	private final HttpClient http = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	LlmClient(LlmConfig config) {
		this.config = config;
	}

	boolean configured() {
		return config.configured();
	}

	String model() {
		return config.model();
	}

	/** The JSON object in the model's answer; every failure is a {@link CheckFailure} with the status to return. */
	JsonNode completeJson(String prompt) {
		if (!config.configured()) {
			throw new CheckFailure(503, "not_configured", "No LLM API key is set on the worker");
		}
		String body = Responses.JSON.writeValueAsString(Map.of(
				"model", config.model(),
				"messages", List.of(Map.of("role", "user", "content", prompt)),
				"temperature", 0.3,
				"max_tokens", config.maxTokens(),
				"response_format", Map.of("type", "json_object")));
		HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl() + "/chat/completions"))
				.timeout(config.timeout())
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + config.apiKey())
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
		// HttpRequest.timeout only covers the wait for the response headers; a provider that sends them and then
		// drips the body would hold the slot for ever, so the whole exchange is bounded here.
		CompletableFuture<HttpResponse<String>> call = http.sendAsync(request,
				HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(), MAX_ANSWER_BYTES));
		HttpResponse<String> response;
		try {
			response = call.get(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException ex) {
			call.cancel(true);
			throw new CheckFailure(504, "timeout", "The model did not answer in time");
		}
		catch (ExecutionException ex) {
			if (ex.getCause() instanceof HttpTimeoutException) {
				throw new CheckFailure(504, "timeout", "The model did not answer in time");
			}
			throw new CheckFailure(502, "llm_unreachable", "The model provider could not be reached");
		}
		catch (InterruptedException ex) {
			call.cancel(true);
			Thread.currentThread().interrupt();
			throw new CheckFailure(500, "internal", "The request was interrupted");
		}
		if (response.statusCode() != 200) {
			// The provider's error text ("invalid key", "insufficient credits", "no such model"...) says what to fix;
			// it never contains the resume, so it may be logged. The caller only gets the status.
			log.warning("model provider answered HTTP " + response.statusCode() + ": " + providerMessage(response.body()));
			throw new CheckFailure(502, "llm_error", "The model provider answered HTTP " + response.statusCode());
		}
		return parseAnswer(response.body());
	}

	private static String providerMessage(String body) {
		try {
			JsonNode error = Responses.JSON.readTree(body).path("error");
			String message = error.isString() ? error.asString("") : error.path("message").asString("");
			return message.length() > 200 ? message.substring(0, 200) : message;
		}
		catch (JacksonException ex) {
			return "(not JSON)";
		}
	}

	private static JsonNode parseAnswer(String responseBody) {
		try {
			String content = Responses.JSON.readTree(responseBody).path("choices").path(0).path("message")
					.path("content").asString("");
			return Responses.JSON.readTree(extractJson(content));
		}
		catch (JacksonException ex) {
			throw new CheckFailure(502, "bad_model_answer", "The model did not answer with valid JSON");
		}
	}

	/** Models sometimes wrap the object in code fences or text despite JSON mode: take '{' to the last '}'. */
	static String extractJson(String content) {
		String trimmed = content.strip();
		int start = trimmed.indexOf('{');
		int end = trimmed.lastIndexOf('}');
		return start >= 0 && end > start ? trimmed.substring(start, end + 1) : trimmed;
	}
}
