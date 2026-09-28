package com.jobsearch.core_api.ai;

import com.jobsearch.core_api.config.AppProperties;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

		RestClient.Builder builder = RestClient.builder()
				.baseUrl(cfg.getBaseUrl())
				.requestFactory(requestFactory)
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.defaultHeader("HTTP-Referer", cfg.getSiteUrl())
				.defaultHeader("X-Title", cfg.getSiteName());

		if (cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) {
			builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getApiKey());
		}
		return builder.build();
	}

	public record ChatMessage(String role, String content) {
	}

	public record ChatRequest(
			String model,
			List<ChatMessage> messages,
			Map<String, Object> response_format
	) {
	}
}
