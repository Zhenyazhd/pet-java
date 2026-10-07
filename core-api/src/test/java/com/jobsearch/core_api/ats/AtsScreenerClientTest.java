package com.jobsearch.core_api.ats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jobsearch.core_api.config.AppProperties;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class AtsScreenerClientTest {

	private static final String ANALYZE = "http://screener/api/analyze";

	private MockRestServiceServer screener;
	private AtsScreenerClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://screener");
		screener = MockRestServiceServer.bindTo(builder).build();
		client = new AtsScreenerClient(builder.build(), new AppProperties(), JsonMapper.builder().build());
	}

	@Test
	void reportIsReturned() {
		screener.expect(requestTo(ANALYZE)).andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

		ScreenerResult.Report report = assertInstanceOf(ScreenerResult.Report.class, client.fullScore("cv", "jd"));

		assertEquals(0, report.root().path("results").size());
	}

	@Test
	void serverErrorIsFailed() {
		screener.expect(requestTo(ANALYZE)).andRespond(withServerError());

		assertInstanceOf(ScreenerResult.Failed.class, client.fullScore("cv", "jd"));
	}

	@Test
	void errorFieldIsFailed() {
		screener.expect(requestTo(ANALYZE)).andRespond(withSuccess("{\"error\":\"quota\"}", MediaType.APPLICATION_JSON));

		assertInstanceOf(ScreenerResult.Failed.class, client.fullScore("cv", "jd"));
	}

	@Test
	void malformedJsonIsFailed() {
		screener.expect(requestTo(ANALYZE)).andRespond(withSuccess("<html>oops</html>", MediaType.TEXT_HTML));

		assertInstanceOf(ScreenerResult.Failed.class, client.fullScore("cv", "jd"));
	}

	@Test
	void timeoutIsFailed() {
		screener.expect(requestTo(ANALYZE)).andRespond(withException(new IOException(new HttpTimeoutException("timed out"))));

		assertInstanceOf(ScreenerResult.Failed.class, client.fullScore("cv", "jd"));
	}

	@Test
	void connectionFailureIsUnavailable() {
		screener.expect(requestTo(ANALYZE)).andRespond(withException(new IOException("Connection refused")));

		assertInstanceOf(ScreenerResult.Unavailable.class, client.fullScore("cv", "jd"));
	}
}
