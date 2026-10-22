package com.jobsearch.core_api.ats;

import com.jobsearch.core_api.common.RetryAfter;
import com.jobsearch.core_api.common.Strings;
import com.jobsearch.core_api.config.AppProperties;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Calls {@code POST /score} on tools/ats-worker, which reads the text out of the resume PDF and asks a model to score
 * it on six ATS platforms.
 * Failures come back as a {@link ScoreResult}, not exceptions.
 */
@Component
public class AtsScoreClient {

	private static final Logger log = LoggerFactory.getLogger(AtsScoreClient.class);
	/** Worker codes that describe the PDF itself, so the user can act on them. */
	private static final Set<String> USER_FACING_CODES = Set.of(
			"no_text_layer", "not_a_pdf", "unreadable_pdf", "encrypted", "too_many_pages", "too_much_text", "page_too_large");
	private static final String SCORING_FAILED_MESSAGE = "The ATS score could not be computed. Please try again later.";

	private final RestClient restClient;
	private final AppProperties appProperties;
	private final ObjectMapper objectMapper;

	public AtsScoreClient(
			@Qualifier("atsWorkerScoreRestClient") RestClient restClient,
			AppProperties appProperties,
			ObjectMapper objectMapper
	) {
		this.restClient = restClient;
		this.appProperties = appProperties;
		this.objectMapper = objectMapper;
	}

	public ScoreResult fullScore(byte[] pdf, String jobDescription) {
		AppProperties.AtsWorker cfg = appProperties.getAtsWorker();
		int timeoutSeconds = cfg.getScoreTimeoutSeconds();
		log.info("ATS score pdfBytes={} jdChars={} timeoutSec={}", pdf.length, jobDescription.length(), timeoutSeconds);

		try {
			return restClient.post()
					.uri("/score")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("pdfBase64", Base64.getEncoder().encodeToString(pdf), "jobDescription", jobDescription))
					.exchange((request, response) -> {
						int status = response.getStatusCode().value();
						String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
						return switch (status) {
							case 200 -> report(body);
							// Every slot is taken: the same request may well work shortly.
							case 503 -> busyOrNotConfigured(body, response.getHeaders().getFirst("Retry-After"));
							case 504 -> {
								log.warn("ats-worker /score timed out ({})", errorCode(body));
								yield new ScoreResult.Failed("The ATS score took too long. Try again in a moment.");
							}
							// The PDF itself is the problem (no text, encrypted, too long...): say so.
							case 422 -> unprocessable(body);
							// The model provider failed or was unreachable from the worker: transient, unlike a bad answer.
							case 502 -> badGateway(body);
							case 401 -> {
								log.error("ats-worker refused the token: ATS_WORKER_TOKEN of core-api and the worker differ");
								yield new ScoreResult.Failed(SCORING_FAILED_MESSAGE);
							}
							default -> {
								log.error("ats-worker /score HTTP {} body={}", status, Strings.abbreviate(body, 400));
								yield new ScoreResult.Failed(SCORING_FAILED_MESSAGE);
							}
						};
					});
		}
		catch (RuntimeException ex) {
			if (isReadTimeout(ex)) {
				log.warn("ats-worker /score timed out after {}s", timeoutSeconds);
				return new ScoreResult.Failed(
						"The ATS score took longer than " + timeoutSeconds + "s. Try again, or shorten the vacancy text.");
			}
			return new ScoreResult.Unavailable(cfg.getBaseUrl() + ": " + ex);
		}
	}

	/**
	 * The worker took too long to answer. A connect timeout is not that: the worker was not reachable at all, which
	 * is worth a retry. (A timeout here does not stop the worker, which still finishes the model call.)
	 */
	private static boolean isReadTimeout(Throwable ex) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (t instanceof HttpConnectTimeoutException) {
				return false;
			}
			if (t instanceof HttpTimeoutException) {
				return true;
			}
		}
		return false;
	}

	private ScoreResult report(String body) {
		try {
			JsonNode root = objectMapper.readTree(body);
			if (root.hasNonNull("error")) {
				log.error("ats-worker reported an error: {}", root.path("error").asString());
				return new ScoreResult.Failed(SCORING_FAILED_MESSAGE);
			}
			return new ScoreResult.Report(root);
		}
		catch (JacksonException ex) {
			// Not the content: a model answer can echo the resume.
			log.error("ats-worker returned malformed JSON chars={}", body.length());
			return new ScoreResult.Failed(SCORING_FAILED_MESSAGE);
		}
	}

	private ScoreResult badGateway(String body) {
		String code = errorCode(body);
		if ("llm_unreachable".equals(code) || "llm_error".equals(code)) {
			log.warn("ats-worker /score: {} ({})", code, errorMessage(body));
			return new ScoreResult.Unavailable("ats-worker: " + code);
		}
		log.error("ats-worker /score HTTP 502 code={} message={}", code, errorMessage(body));
		return new ScoreResult.Failed(SCORING_FAILED_MESSAGE);
	}

	private ScoreResult unprocessable(String body) {
		String code = errorCode(body);
		if (USER_FACING_CODES.contains(code)) {
			return new ScoreResult.Failed(errorMessage(body));
		}
		log.error("ats-worker /score HTTP 422 code={}", code);
		return new ScoreResult.Failed(SCORING_FAILED_MESSAGE);
	}

	private ScoreResult busyOrNotConfigured(String body, String retryAfter) {
		String code = errorCode(body);
		if ("not_configured".equals(code)) {
			log.error("ats-worker has no LLM API key (OPENROUTER_API_KEY), scoring is not possible");
			return new ScoreResult.Failed("The ATS score is not available yet.");
		}
		return new ScoreResult.Busy(RetryAfter.seconds(retryAfter, 2));
	}

	private String errorMessage(String body) {
		try {
			return objectMapper.readTree(body).path("message").asString(SCORING_FAILED_MESSAGE);
		}
		catch (JacksonException ex) {
			return SCORING_FAILED_MESSAGE;
		}
	}

	private String errorCode(String body) {
		try {
			return objectMapper.readTree(body).path("error").asString("");
		}
		catch (JacksonException ex) {
			return "";
		}
	}
}
