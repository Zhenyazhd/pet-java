package com.jobsearch.core_api.compile;

import com.jobsearch.core_api.config.AppProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP client for tools/latex-worker. */
@Configuration
public class LatexWorkerConfig {

	@Bean
	RestClient latexWorkerRestClient(AppProperties appProperties) {
		AppProperties.LatexWorker cfg = appProperties.getLatexWorker();
		if (cfg.getToken() == null || cfg.getToken().isBlank()) {
			throw new IllegalStateException("LATEX_WORKER_TOKEN / app.latex-worker.token is required");
		}
		// The worker is a plain HTTP/1.1 server (JDK HttpServer).
		HttpClient httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(cfg.getConnectTimeoutSeconds()))
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(cfg.getReadTimeoutSeconds()));
		return RestClient.builder()
				.baseUrl(cfg.getBaseUrl().replaceAll("/+$", ""))
				.requestFactory(requestFactory)
				.defaultHeader("X-Worker-Token", cfg.getToken())
				.build();
	}
}
