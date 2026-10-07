package com.jobsearch.core_api.ats;

import com.jobsearch.core_api.common.Strings;
import com.jobsearch.core_api.config.AppProperties;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Calls ATS Screener POST /api/analyze (full-score mode); failures come back as a {@link ScreenerResult}, not exceptions. */
@Component
public class AtsScreenerClient {

	private static final Logger log = LoggerFactory.getLogger(AtsScreenerClient.class);
	private static final String SCORING_FAILED_MESSAGE = "ATS Screener could not score this resume. Please try again later.";

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

	public ScreenerResult fullScore(String resumeText, String jobDescription) {
		AppProperties.AtsScreener cfg = appProperties.getAtsScreener();
		int timeoutSeconds = cfg.getTimeoutSeconds();
		Map<String, Object> body = Map.of(
				"mode", "full-score",
				"resumeText", resumeText,
				"jobDescription", jobDescription
		);

		log.info(
				"ATS Screener full-score resumeChars={} jdChars={} timeoutSec={}",
				resumeText.length(),
				jobDescription.length(),
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
		} catch (RestClientResponseException ex) {
			log.error("ATS Screener HTTP {} body={}", ex.getStatusCode().value(), Strings.abbreviate(ex.getResponseBodyAsString(), 400));
			return new ScreenerResult.Failed(SCORING_FAILED_MESSAGE);
		} catch (Exception ex) {
			if (isTimeout(ex)) {
				log.warn("ATS Screener timed out after {}s", timeoutSeconds);
				return new ScreenerResult.Failed(
						"ATS Screener took longer than " + timeoutSeconds + "s. Try again, or shorten the vacancy text.");
			}
			return new ScreenerResult.Unavailable(cfg.getBaseUrl() + ": " + ex);
		}

		if (raw == null || raw.isBlank()) {
			log.error("ATS Screener returned an empty response");
			return new ScreenerResult.Failed(SCORING_FAILED_MESSAGE);
		}
		JsonNode root;
		try {
			root = objectMapper.readTree(raw);
		} catch (JacksonException ex) {
			log.error("ATS Screener returned malformed JSON: {}", Strings.abbreviate(raw, 400));
			return new ScreenerResult.Failed(SCORING_FAILED_MESSAGE);
		}
		if (root.hasNonNull("error")) {
			log.error("ATS Screener reported an error: {}", root.path("error").asString());
			return new ScreenerResult.Failed(SCORING_FAILED_MESSAGE);
		}
		return new ScreenerResult.Report(root);
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
}
