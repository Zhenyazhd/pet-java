package com.jobsearch.core_api.readability;

import com.jobsearch.core_api.config.AppProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP client for tools/ats-worker. */
@Configuration
public class AtsWorkerConfig {

	/** For the quick PDF check. */
	@Bean
	RestClient atsWorkerRestClient(AppProperties appProperties) {
		return client(appProperties, appProperties.getAtsWorker().getReadTimeoutSeconds());
	}

	/** For the model call behind the vacancy match, which takes much longer. */
	@Bean
	RestClient atsWorkerScoreRestClient(AppProperties appProperties) {
		return client(appProperties, appProperties.getAtsWorker().getScoreTimeoutSeconds());
	}

	private static RestClient client(AppProperties appProperties, int readTimeoutSeconds) {
		AppProperties.AtsWorker cfg = appProperties.getAtsWorker();
		if (cfg.isEnabled() && (cfg.getToken() == null || cfg.getToken().isBlank())) {
			throw new IllegalStateException("ATS_WORKER_TOKEN / app.ats-worker.token is required while the ATS worker is enabled");
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
