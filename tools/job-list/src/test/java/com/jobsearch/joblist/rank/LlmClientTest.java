package com.jobsearch.joblist.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jobsearch.joblist.TestProperties;
import java.io.IOException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

/** The real client against a fake OpenRouter. */
class LlmClientTest {

	private static final String URL = "http://llm.test/chat/completions";

	private MockRestServiceServer server;
	private LlmClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://llm.test");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new LlmClient(builder.build(), TestProperties.defaults());
	}

	private static String answer(String content, String finishReason, String cost) {
		return """
				{"choices": [{"finish_reason": "%s", "message": {"content": %s}}],
				 "usage": {"cost": %s, "prompt_tokens": 100, "completion_tokens": 20}}""".formatted(finishReason,
				tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(content), cost);
	}

	@Test
	void theRequestIsLimitedInLengthAndAsksForJson() {
		server.expect(requestTo(URL))
				.andExpect(jsonPath("$.max_tokens").value(1100))
				.andExpect(jsonPath("$.response_format.type").value("json_object"))
				.andExpect(jsonPath("$.usage.include").value(true))
				.andRespond(withSuccess(answer("{\"results\": []}", "stop", "0.002"), MediaType.APPLICATION_JSON));

		LlmClient.Completion completion = client.complete("system", "user", 1100);

		assertNull(completion.error());
		assertEquals("{\"results\": []}", completion.content());
		assertEquals(0, new BigDecimal("0.002").compareTo(completion.costUsd()));
		server.verify();
	}

	@Test
	void jsonWrappedInFencesOrProseIsTakenOut() {
		server.expect(requestTo(URL)).andRespond(withSuccess(answer("```json\n{\"results\": [1]}\n```", "stop", "0"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess(answer("Here is my answer:\n{\"results\": [2]}\nHope it helps.", "stop", "0"), MediaType.APPLICATION_JSON));

		assertEquals("{\"results\": [1]}", client.complete("s", "u", 100).content());
		assertEquals("{\"results\": [2]}", client.complete("s", "u", 100).content());
	}

	@Test
	void anAnswerCutOffByTheLengthLimitIsMarkedAndItsCostIsKept() {
		server.expect(requestTo(URL)).andRespond(withSuccess(answer("{\"results\": [", "length", "0.003"), MediaType.APPLICATION_JSON));

		LlmClient.Completion completion = client.complete("s", "u", 100);

		assertTrue(completion.cutOff());
		assertNull(completion.error(), "a cut-off answer is not a failed call");
		assertNull(completion.content());
		assertEquals(0, new BigDecimal("0.003").compareTo(completion.costUsd()));
		assertTrue(completion.costKnown());
	}

	@Test
	void aBraceInTheProseAfterTheJsonDoesNotBreakIt() {
		server.expect(requestTo(URL)).andRespond(withSuccess(
				answer("{\"results\": [{\"id\": 1, \"reason\": \"has a } in it\"}]} and then {if} more prose", "stop", "0"), MediaType.APPLICATION_JSON));

		assertEquals("{\"results\": [{\"id\": 1, \"reason\": \"has a } in it\"}]}", client.complete("s", "u", 100).content());
	}

	@Test
	void aBraceInTheProseBeforeTheJsonDoesNotBreakItEither() {
		server.expect(requestTo(URL)).andRespond(withSuccess(
				answer("Sure {if you want}: {\"results\": []}", "stop", "0"), MediaType.APPLICATION_JSON));

		assertEquals("{\"results\": []}", client.complete("s", "u", 100).content());
	}

	@Test
	void aHugeTextOfOpeningBracesIsScannedInBoundedTime() {
		for (String text : new String[] {"{".repeat(1_000_000), "{\"a\":".repeat(200_000)}) {
			long started = System.nanoTime();

			LlmClient.extractJson(text);

			assertTrue((System.nanoTime() - started) / 1_000_000 < 2000, "took too long on a hostile answer");
		}
	}

	@Test
	void costIsOnlyKnownWhenTheProviderReportedIt() {
		server.expect(requestTo(URL)).andRespond(withSuccess(
				"{\"choices\": [{\"finish_reason\": \"stop\", \"message\": {\"content\": \"{}\"}}], \"usage\": {\"prompt_tokens\": 5}}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
		server.expect(requestTo(URL)).andRespond(MockRestResponseCreators.withException(new IOException("read timed out")));

		assertFalse(client.complete("s", "u", 100).costKnown(), "no usage.cost: the worst case must be counted");
		assertTrue(client.complete("s", "u", 100).costKnown(), "a refusal happened before anything was generated");
		assertFalse(client.complete("s", "u", 100).costKnown(), "a 5xx may have been billed");
		assertFalse(client.complete("s", "u", 100).costKnown(), "a timeout may have been billed");
	}

	@Test
	void refusalsAreExplainedByTheirCauseWithoutTheProvidersText() {
		for (HttpStatus status : new HttpStatus[] {HttpStatus.UNAUTHORIZED, HttpStatus.PAYMENT_REQUIRED, HttpStatus.NOT_FOUND, HttpStatus.TOO_MANY_REQUESTS, HttpStatus.BAD_GATEWAY}) {
			server.expect(requestTo(URL)).andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body("{\"error\": {\"message\": \"secret detail\"}}"));
		}

		assertEquals("The model provider refused the API key", client.complete("s", "u", 100).error());
		assertEquals("The model provider account has no credit", client.complete("s", "u", 100).error());
		assertEquals("The model provider does not know the configured model", client.complete("s", "u", 100).error());
		assertEquals("The model provider is rate limiting the requests", client.complete("s", "u", 100).error());
		assertEquals("The model could not be reached", client.complete("s", "u", 100).error());
	}

	@Test
	void anUnreachableProviderIsAnErrorNotAnException() {
		server.expect(requestTo(URL)).andRespond(MockRestResponseCreators.withException(new IOException("refused")));

		assertEquals("The model could not be reached", client.complete("s", "u", 100).error());
	}
}
