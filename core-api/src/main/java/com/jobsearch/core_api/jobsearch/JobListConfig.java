package com.jobsearch.core_api.jobsearch;

import com.jobsearch.core_api.config.AppProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP client for job-list (tools/job-list). Built even when the feature is off; nothing calls it then. */
@Configuration
public class JobListConfig {

	/** The read timeout of the counter call: it must never hold up the page. */
	static final Duration COUNT_READ_TIMEOUT = Duration.ofSeconds(2);

	@Bean
	public RestClient jobListRestClient(AppProperties appProperties) {
		return build(appProperties, Duration.ofSeconds(appProperties.getJobList().getReadTimeoutSeconds()));
	}

	/** Same job-list, short read timeout, for the "unranked" counter shown on every page load. */
	@Bean
	public RestClient jobListCountRestClient(AppProperties appProperties) {
		return build(appProperties, COUNT_READ_TIMEOUT);
	}

	private static RestClient build(AppProperties appProperties, Duration readTimeout) {
		AppProperties.JobList cfg = appProperties.getJobList();
		if (cfg.isConfigured()) {
			requireHttpUrl(cfg.getBaseUrl());
		}
		HttpClient httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(cfg.getConnectTimeoutSeconds()))
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(readTimeout);
		RestClient.Builder builder = RestClient.builder()
				.baseUrl(cfg.getBaseUrl().replaceAll("/+$", ""))
				.requestFactory(requestFactory)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
		if (cfg.isConfigured()) {
			builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getToken().strip());
		}
		return builder.build();
	}

	private static void requireHttpUrl(String baseUrl) {
		String message = "app.job-list.base-url (JOB_LIST_BASE_URL) must be an absolute http(s) URL when JOB_LIST_TOKEN is set";
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalStateException(message);
		}
		try {
			java.net.URI uri = java.net.URI.create(baseUrl.strip());
			String scheme = uri.getScheme();
			if (!uri.isAbsolute() || uri.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
				throw new IllegalStateException(message);
			}
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException(message);
		}
	}
}
