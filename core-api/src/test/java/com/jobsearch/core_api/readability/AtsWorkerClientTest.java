package com.jobsearch.core_api.readability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class AtsWorkerClientTest {

	private MockRestServiceServer worker;
	private AtsWorkerClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://worker");
		worker = MockRestServiceServer.bindTo(builder).build();
		client = new AtsWorkerClient(builder.build(), JsonMapper.builder().build());
	}

	@Test
	void theRequestCarriesThePdfAsBase64AndTheExpectedText() {
		worker.expect(requestTo("http://worker/check"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.pdfBase64").value("JVBERg=="))
				.andExpect(jsonPath("$.expected").value("Jane Doe"))
				.andRespond(withSuccess("{\"verdict\":\"ok\",\"score\":99}", MediaType.APPLICATION_JSON));

		AtsWorkerResult.Report result = assertInstanceOf(AtsWorkerResult.Report.class,
				client.check(new byte[] {37, 80, 68, 70}, "Jane Doe"));

		assertEquals("ok", result.report().path("verdict").asString());
	}

	@Test
	void aPdfTheWorkerRefusesIsRejectedWithItsCode() {
		worker.expect(requestTo("http://worker/check")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"encrypted\",\"message\":\"The PDF is password protected\"}"));

		AtsWorkerResult.Rejected result = assertInstanceOf(AtsWorkerResult.Rejected.class, client.check(new byte[0], ""));

		assertEquals("encrypted", result.code());
		assertEquals("The PDF is password protected", result.message());
	}

	@Test
	void aRefusedTokenIsAConfigurationProblemNotAVerdictOnThePdf() {
		worker.expect(requestTo("http://worker/check")).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
				.contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"unauthorized\",\"message\":\"x\"}"));

		assertInstanceOf(AtsWorkerResult.Unavailable.class, client.check(new byte[0], ""));
	}

	@Test
	void busyCarriesRetryAfter() {
		worker.expect(requestTo("http://worker/check")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
				.header("Retry-After", "4").contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"busy\",\"message\":\"All check slots are busy\"}"));

		assertEquals(4, assertInstanceOf(AtsWorkerResult.Busy.class, client.check(new byte[0], "")).retryAfterSeconds());
	}

	@Test
	void timeoutsServerErrorsAndNetworkFailuresAreUnavailable() {
		worker.expect(requestTo("http://worker/check")).andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));
		worker.expect(requestTo("http://worker/check")).andRespond(withException(new IOException("refused")));

		assertInstanceOf(AtsWorkerResult.Unavailable.class, client.check(new byte[0], ""));
		assertInstanceOf(AtsWorkerResult.Unavailable.class, client.check(new byte[0], ""));
	}
}
