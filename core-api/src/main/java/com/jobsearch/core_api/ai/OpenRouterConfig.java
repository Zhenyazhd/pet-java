package com.jobsearch.core_api.ai;

import com.jobsearch.core_api.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Builds the RestClient used to call OpenRouter. */
@Configuration
public class OpenRouterConfig {

	@Bean
	RestClient openRouterRestClient(AppProperties appProperties) {
		AppProperties.OpenRouter cfg = appProperties.getOpenRouter();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
		requestFactory.setReadTimeout(java.time.Duration.ofSeconds(90));

		return RestClient.builder()
				.baseUrl(cfg.getBaseUrl())
				.requestFactory(requestFactory)
				.defaultHeader("HTTP-Referer", cfg.getSiteUrl())
				.defaultHeader("X-Title", cfg.getSiteName())
				// OpenRouterClient refuses to call out when the key is blank.
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getApiKey())
				.build();
	}
}
