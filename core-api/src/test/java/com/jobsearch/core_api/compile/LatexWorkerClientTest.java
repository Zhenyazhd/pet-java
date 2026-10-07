package com.jobsearch.core_api.compile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

class LatexWorkerClientTest {

	private MockRestServiceServer worker;
	private LatexWorkerClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://worker");
		worker = MockRestServiceServer.bindTo(builder).build();
		client = new LatexWorkerClient(builder.build(), JsonMapper.builder().build());
	}

	@Test
	void okIsPdf() {
		byte[] pdf = { 37, 80, 68, 70 };
		worker.expect(requestTo("http://worker/compile")).andRespond(withSuccess(pdf, MediaType.APPLICATION_PDF));

		WorkerResult.Pdf result = assertInstanceOf(WorkerResult.Pdf.class, client.compile("x"));

		assertArrayEquals(pdf, result.bytes());
	}

	@Test
	void compileErrorIsRejectedWithItsLog() {
		worker.expect(requestTo("http://worker/compile")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"compile_error\",\"message\":\"Undefined control sequence\"}"));

		WorkerResult.Rejected result = assertInstanceOf(WorkerResult.Rejected.class, client.compile("x"));

		assertEquals("compile_error", result.code());
		assertEquals("Undefined control sequence", result.message());
	}

	@Test
	void serviceUnavailableIsBusyWithRetryAfter() {
		worker.expect(requestTo("http://worker/compile")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
				.header("Retry-After", "7")
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"busy\",\"message\":\"All compile slots are busy\"}"));

		WorkerResult.Busy result = assertInstanceOf(WorkerResult.Busy.class, client.compile("x"));

		assertEquals(7, result.retryAfterSeconds());
	}

	@Test
	void serverErrorAndNetworkFailureAreUnavailable() {
		worker.expect(requestTo("http://worker/compile")).andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));
		worker.expect(requestTo("http://worker/compile")).andRespond(withException(new IOException("refused")));

		assertInstanceOf(WorkerResult.Unavailable.class, client.compile("x"));
		assertInstanceOf(WorkerResult.Unavailable.class, client.compile("x"));
	}
}
