package com.jobsearch.core_api.compile;

import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Calls {@code POST /compile} on tools/latex-worker; status codes are documented in its SPEC.md §3.2. */
@Component
public class LatexWorkerClient {

	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public LatexWorkerClient(@Qualifier("latexWorkerRestClient") RestClient restClient, ObjectMapper objectMapper) {
		this.restClient = restClient;
		this.objectMapper = objectMapper;
	}

	public WorkerResult compile(String source) {
		try {
			return restClient.post()
					.uri("/compile")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("source", source))
					.exchange((request, response) -> {
						int status = response.getStatusCode().value();
						byte[] body = response.getBody().readAllBytes();
						return switch (status) {
							case 200 -> new WorkerResult.Pdf(body);
							case 400, 413, 422 -> rejected(body);
							case 503 -> new WorkerResult.Busy(retryAfterSeconds(response.getHeaders().getFirst("Retry-After")));
							default -> new WorkerResult.Unavailable("HTTP " + status);
						};
					});
		}
		catch (RuntimeException ex) {
			// Network errors, timeouts and unreadable responses are all worth a retry.
			return new WorkerResult.Unavailable(ex.getMessage());
		}
	}

	private WorkerResult.Rejected rejected(byte[] body) {
		JsonNode error = objectMapper.readTree(body);
		return new WorkerResult.Rejected(error.path("error").asString("rejected"), error.path("message").asString(""));
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
