package com.jobsearch.joblist.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientsConfig {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
	private static final Duration APIFY_READ_TIMEOUT = Duration.ofSeconds(60);

	@Bean
	RestClient apifyRestClient(JobListProperties properties) {
		JobListProperties.Apify apify = properties.apify();
		return RestClient.builder()
				.baseUrl(apify.baseUrl())
				.requestFactory(requestFactory(APIFY_READ_TIMEOUT))
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apify.token())
				.build();
	}

	@Bean
	RestClient openRouterRestClient(JobListProperties properties) {
		JobListProperties.Llm llm = properties.llm();
		return RestClient.builder()
				.baseUrl(llm.baseUrl())
				.requestFactory(requestFactory(llm.timeout()))
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + llm.apiKey())
				.build();
	}

	private static JdkClientHttpRequestFactory requestFactory(Duration readTimeout) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
		factory.setReadTimeout(readTimeout);
		return factory;
	}
}
