package com.jobsearch.core_api.readability;

import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Calls {@code POST /check} on tools/ats-worker; its status codes are documented in that README. */
@Component
public class AtsWorkerClient {

	private static final Logger log = LoggerFactory.getLogger(AtsWorkerClient.class);

	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public AtsWorkerClient(@Qualifier("atsWorkerRestClient") RestClient restClient, ObjectMapper objectMapper) {
		this.restClient = restClient;
		this.objectMapper = objectMapper;
	}

	/** @param expected the plain text the PDF was built from; the worker compares the PDF's text with it */
	public AtsWorkerResult check(byte[] pdf, String expected) {
		try {
			return restClient.post()
					.uri("/check")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("pdfBase64", Base64.getEncoder().encodeToString(pdf), "expected", expected))
					.exchange((request, response) -> {
						int status = response.getStatusCode().value();
						byte[] body = response.getBody().readAllBytes();
						return switch (status) {
							case 200 -> new AtsWorkerResult.Report(objectMapper.readTree(body));
							case 400, 413, 422 -> rejected(body);
							// Not the user's PDF: the two services disagree about the token. Say so in the log, retry later.
							case 401 -> {
								log.error("ats-worker refused the token: ATS_WORKER_TOKEN of core-api and the worker differ");
								yield new AtsWorkerResult.Unavailable("ats-worker refused the token");
							}
							case 503 -> new AtsWorkerResult.Busy(retryAfterSeconds(response.getHeaders().getFirst("Retry-After")));
							default -> new AtsWorkerResult.Unavailable("HTTP " + status);
						};
					});
		}
		catch (RuntimeException ex) {
			// Network errors, timeouts and unreadable responses are all worth a retry.
			return new AtsWorkerResult.Unavailable(ex.getMessage());
		}
	}

	private AtsWorkerResult.Rejected rejected(byte[] body) {
		try {
			JsonNode error = objectMapper.readTree(body);
			return new AtsWorkerResult.Rejected(error.path("error").asString("rejected"), error.path("message").asString(""));
		}
		catch (JacksonException ex) {
			return new AtsWorkerResult.Rejected("rejected", "");
		}
	}

	private static long retryAfterSeconds(String header) {
		try {
			return header == null ? 1 : Math.max(1, Long.parseLong(header.strip()));
		}
		catch (NumberFormatException ex) {
			return 1;
		}
	}
}
