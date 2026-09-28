package com.jobsearch.core_api.ats;

import com.jobsearch.core_api.config.AppProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP client for the local ATS Screener (tools/ats-screener). */
@Configuration
public class AtsScreenerConfig {

	@Bean
	@Qualifier("atsScreenerRestClient")
	RestClient atsScreenerRestClient(AppProperties appProperties) {
		AppProperties.AtsScreener cfg = appProperties.getAtsScreener();
		// Force HTTP/1.1: JDK HttpClient + Vite/SvelteKit over HTTP/2 hangs until read timeout
		// (Python/curl succeed in ~15s; Java otherwise sits for the full timeout).
		HttpClient httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(10))
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(30, cfg.getTimeoutSeconds())));
		return RestClient.builder()
				.baseUrl(cfg.getBaseUrl().replaceAll("/+$", ""))
				.requestFactory(requestFactory)
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.defaultHeader(HttpHeaders.CONNECTION, "close")
				.build();
	}
}
