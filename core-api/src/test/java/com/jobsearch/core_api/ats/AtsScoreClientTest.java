package com.jobsearch.core_api.ats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jobsearch.core_api.config.AppProperties;
import java.io.IOException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class AtsScoreClientTest {

	private static final String SCORE = "http://worker/score";
	private static final byte[] PDF = {37, 80, 68, 70, 45};

	private MockRestServiceServer worker;
	private AtsScoreClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://worker");
		worker = MockRestServiceServer.bindTo(builder).build();
		client = new AtsScoreClient(builder.build(), new AppProperties(), JsonMapper.builder().build());
	}

	@Test
	void reportIsReturnedAndTheRequestCarriesThePdfAndTheVacancy() {
		worker.expect(requestTo(SCORE))
				.andExpect(jsonPath("$.pdfBase64").value("JVBERi0="))
				.andExpect(jsonPath("$.jobDescription").value("jd"))
				.andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

		ScoreResult.Report report = assertInstanceOf(ScoreResult.Report.class, client.fullScore(PDF, "jd"));

		assertEquals(0, report.root().path("results").size());
	}

	@Test
	void serverErrorAndBadGatewayAreFailed() {
		worker.expect(requestTo(SCORE)).andRespond(withServerError());
		worker.expect(requestTo(SCORE)).andRespond(withStatus(HttpStatus.BAD_GATEWAY)
				.contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"bad_model_answer\"}"));

		assertInstanceOf(ScoreResult.Failed.class, client.fullScore(PDF, "jd"));
		assertInstanceOf(ScoreResult.Failed.class, client.fullScore(PDF, "jd"));
	}

	@Test
	void errorFieldAndMalformedJsonAreFailed() {
		worker.expect(requestTo(SCORE)).andRespond(withSuccess("{\"error\":\"quota\"}", MediaType.APPLICATION_JSON));
		worker.expect(requestTo(SCORE)).andRespond(withSuccess("<html>oops</html>", MediaType.TEXT_HTML));

		assertInstanceOf(ScoreResult.Failed.class, client.fullScore(PDF, "jd"));
		assertInstanceOf(ScoreResult.Failed.class, client.fullScore(PDF, "jd"));
	}

	@Test
	void aBusyWorkerIsWorthARetryButAMissingKeyIsNot() {
		worker.expect(requestTo(SCORE)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
				.header("Retry-After", "5").contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"busy\"}"));
		worker.expect(requestTo(SCORE)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
				.contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"not_configured\"}"));

		// busy does not use up an attempt and carries the worker's Retry-After
		assertEquals(5, assertInstanceOf(ScoreResult.Busy.class, client.fullScore(PDF, "jd")).retryAfterSeconds());
		assertInstanceOf(ScoreResult.Failed.class, client.fullScore(PDF, "jd"));
	}

	@Test
	void aPdfTheWorkerCannotReadGivesTheUserItsMessageAndATimeoutIsFailed() {
		worker.expect(requestTo(SCORE)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"no_text_layer\",\"message\":\"The PDF has no readable text\"}"));
		worker.expect(requestTo(SCORE)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
				.contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"internal_detail\",\"message\":\"secret detail\"}"));
		worker.expect(requestTo(SCORE)).andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT)
				.contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"timeout\"}"));

		assertEquals("The PDF has no readable text",
				assertInstanceOf(ScoreResult.Failed.class, client.fullScore(PDF, "jd")).message());
		// a code that does not describe the PDF is not shown to the user
		String hidden = assertInstanceOf(ScoreResult.Failed.class, client.fullScore(PDF, "jd")).message();
		assertEquals("The ATS score could not be computed. Please try again later.", hidden);
		assertInstanceOf(ScoreResult.Failed.class, client.fullScore(PDF, "jd"));
	}

	@Test
	void timeoutIsFailed() {
		worker.expect(requestTo(SCORE)).andRespond(withException(new IOException(new HttpTimeoutException("timed out"))));

		assertInstanceOf(ScoreResult.Failed.class, client.fullScore(PDF, "jd"));
	}

	@Test
	void aConnectTimeoutIsUnavailableNotAReadTimeout() {
		worker.expect(requestTo(SCORE)).andRespond(withException(new HttpConnectTimeoutException("connect timed out")));

		assertInstanceOf(ScoreResult.Unavailable.class, client.fullScore(PDF, "jd"));
	}

	@Test
	void aModelProviderFailureIsRetriedButABadAnswerIsNot() {
		for (String code : new String[] {"llm_unreachable", "llm_error", "bad_model_answer"}) {
			worker.expect(requestTo(SCORE)).andRespond(withStatus(HttpStatus.BAD_GATEWAY)
					.contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"" + code + "\"}"));
		}

		assertInstanceOf(ScoreResult.Unavailable.class, client.fullScore(PDF, "jd"));
		assertInstanceOf(ScoreResult.Unavailable.class, client.fullScore(PDF, "jd"));
		assertInstanceOf(ScoreResult.Failed.class, client.fullScore(PDF, "jd"));
	}

	@Test
	void connectionFailureIsUnavailable() {
		worker.expect(requestTo(SCORE)).andRespond(withException(new IOException("Connection refused")));

		assertInstanceOf(ScoreResult.Unavailable.class, client.fullScore(PDF, "jd"));
	}
}
