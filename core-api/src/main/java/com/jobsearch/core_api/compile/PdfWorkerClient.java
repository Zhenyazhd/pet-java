package com.jobsearch.core_api.compile;

import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Calls {@code POST /compile} (always in fit mode) on tools/pdf-worker; status codes are documented in its SPEC.md §3.2. */
@Component
public class PdfWorkerClient {

	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public PdfWorkerClient(@Qualifier("pdfWorkerRestClient") RestClient restClient, ObjectMapper objectMapper) {
		this.restClient = restClient;
		this.objectMapper = objectMapper;
	}

	public WorkerResult compile(String source) {
		try {
			return restClient.post()
					.uri("/compile")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("source", source, "fit", true))
					.exchange((request, response) -> {
						int status = response.getStatusCode().value();
						byte[] body = response.getBody().readAllBytes();
						return switch (status) {
							case 200 -> new WorkerResult.Pdf(body, layout(response.getHeaders()));
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

	/**
	 * Null when the count or fill header is missing or unreadable: the PDF is still good without the metrics. The
	 * scale is optional on its own (null when missing or garbled).
	 */
	private static PageLayout layout(HttpHeaders headers) {
		try {
			String pages = headers.getFirst("X-Page-Count");
			String fill = headers.getFirst("X-Page-Fill");
			return pages == null || fill == null
					? null
					: new PageLayout(Integer.parseInt(pages.strip()), Double.parseDouble(fill.strip()), scale(headers.getFirst("X-Page-Scale")));
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private static Double scale(String header) {
		try {
			double scale = header == null ? Double.NaN : Double.parseDouble(header.strip());
			return Double.isFinite(scale) && scale > 0 ? scale : null;
		}
		catch (NumberFormatException ex) {
			return null;
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
