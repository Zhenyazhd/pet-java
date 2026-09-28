package com.jobsearch.core_api.ats;

import com.jobsearch.core_api.config.AppProperties;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
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

/** Calls ATS Screener POST /api/analyze (full-score mode). */
@Component
public class AtsScreenerClient {

	private static final Logger log = LoggerFactory.getLogger(AtsScreenerClient.class);

	private final RestClient restClient;
	private final AppProperties appProperties;
	private final ObjectMapper objectMapper;

	public AtsScreenerClient(
			@Qualifier("atsScreenerRestClient") RestClient restClient,
			AppProperties appProperties,
			ObjectMapper objectMapper
	) {
		this.restClient = restClient;
		this.appProperties = appProperties;
		this.objectMapper = objectMapper;
	}

	public JsonNode fullScore(String resumeText, String jobDescription) {
		AppProperties.AtsScreener cfg = appProperties.getAtsScreener();
		String baseUrl = cfg.getBaseUrl();
		int timeoutSeconds = cfg.getTimeoutSeconds();
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalStateException(
					"ATS_SCREENER_BASE_URL is not set. Start tools/ats-screener "
							+ "(pnpm exec vite dev --host 127.0.0.1 --port 5174) "
							+ "and set ATS_SCREENER_BASE_URL=http://127.0.0.1:5174"
			);
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("mode", "full-score");
		body.put("resumeText", resumeText);
		if (jobDescription != null && !jobDescription.isBlank()) {
			body.put("jobDescription", jobDescription.strip());
		}

		log.info(
				"ATS Screener full-score resumeChars={} jdChars={} timeoutSec={}",
				resumeText.length(),
				jobDescription == null ? 0 : jobDescription.length(),
				timeoutSeconds
		);

		String raw;
		try {
			raw = restClient.post()
					.uri("/api/analyze")
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(String.class);
		}
		catch (RestClientResponseException ex) {
			log.error("ATS Screener HTTP {} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
			throw new IllegalStateException(
					"ATS Screener error " + ex.getStatusCode().value() + ": " + summarizeError(ex.getResponseBodyAsString()),
					ex
			);
		}
		catch (Exception ex) {
			if (isTimeout(ex)) {
				log.error("ATS Screener timed out after {}s at {}", timeoutSeconds, baseUrl);
				throw new IllegalStateException(
						"ATS Screener timed out after " + timeoutSeconds
								+ "s (Gemini scoring is slow on long resumes). Retry, or set ATS_SCREENER_TIMEOUT_SECONDS higher.",
						ex
				);
			}
			log.error("ATS Screener unreachable at {}: {}", baseUrl, ex.getMessage());
			throw new IllegalStateException(
					"ATS Screener unreachable at " + baseUrl
							+ ". Keep `pnpm exec vite dev --host 127.0.0.1 --port 5174` running.",
					ex
			);
		}

		if (raw == null || raw.isBlank()) {
			throw new IllegalStateException("ATS Screener returned an empty response");
		}

		JsonNode root = objectMapper.readTree(raw);
		if (root.hasNonNull("error")) {
			throw new IllegalStateException("ATS Screener: " + root.path("error").asString());
		}
		return root;
	}

	private static boolean isTimeout(Throwable ex) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (t instanceof HttpTimeoutException) {
				return true;
			}
			String msg = t.getMessage();
			if (msg != null && msg.toLowerCase().contains("timed out")) {
				return true;
			}
			if (msg != null && msg.contains("Request cancelled")) {
				return true;
			}
		}
		return false;
	}

	private static String summarizeError(String body) {
		if (body == null || body.isBlank()) {
			return "(empty body)";
		}
		String trimmed = body.strip();
		return trimmed.length() > 400 ? trimmed.substring(0, 400) + "…" : trimmed;
	}
}
