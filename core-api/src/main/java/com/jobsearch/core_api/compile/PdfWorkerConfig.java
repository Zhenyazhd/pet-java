package com.jobsearch.core_api.compile;

import com.jobsearch.core_api.config.AppProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP client for tools/pdf-worker. */
@Configuration
public class PdfWorkerConfig {

	@Bean
	RestClient pdfWorkerRestClient(AppProperties appProperties) {
		return client(appProperties.getPdfWorker(), appProperties.getPdfWorker().getReadTimeoutSeconds());
	}

	/** Same worker, short read timeout: used where a person is waiting on the answer (AI chat page-fit hint). */
	@Bean
	RestClient pdfWorkerLayoutRestClient(AppProperties appProperties) {
		return client(appProperties.getPdfWorker(), appProperties.getPdfWorker().getLayoutReadTimeoutSeconds());
	}

	private static RestClient client(AppProperties.PdfWorker cfg, int readTimeoutSeconds) {
		if (cfg.getToken() == null || cfg.getToken().isBlank()) {
			throw new IllegalStateException("PDF_WORKER_TOKEN / app.pdf-worker.token is required");
		}
		// The worker is a plain HTTP/1.1 server (JDK HttpServer).
		HttpClient httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(cfg.getConnectTimeoutSeconds()))
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
		return RestClient.builder()
				.baseUrl(cfg.getBaseUrl().replaceAll("/+$", ""))
				.requestFactory(requestFactory)
				.defaultHeader("X-Worker-Token", cfg.getToken())
				.build();
	}
}
