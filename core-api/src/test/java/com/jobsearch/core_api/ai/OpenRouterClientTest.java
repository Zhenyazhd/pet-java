package com.jobsearch.core_api.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jobsearch.core_api.config.AppProperties;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

class OpenRouterClientTest {

	private static final String COMPLETIONS = "http://openrouter/chat/completions";
	private static final String ANSWER = """
			{"choices": [{"message": {"content": "```json\\n{\\"message\\": \\"hi\\"}\\n```"}}]}
			""";

	private static final String CUT_OFF_ANSWER = """
			{"choices": [{"message": {"content": "{\\"message\\": \\"Here is your rew"}, "finish_reason": "length"}],
			 "usage": {"prompt_tokens": 900, "completion_tokens": 8000, "total_tokens": 8900}}
			""";

	private final AppProperties appProperties = new AppProperties();
	private MockRestServiceServer openRouter;
	private OpenRouterClient client;

	@BeforeEach
	void setUp() {
		appProperties.getOpenRouter().setApiKey("test-key");
		client = newClient();
	}

	@Test
	void answerHasCodeFencesStripped() {
		openRouter.expect(requestTo(COMPLETIONS)).andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));

		ChatResult.Answer answer = assertInstanceOf(ChatResult.Answer.class, client.complete("system", "user", null));

		assertEquals("{\"message\": \"hi\"}", answer.content());
	}

	@Test
	void requestCarriesMaxTokens() {
		appProperties.getOpenRouter().setMaxTokens(1234);
		openRouter.expect(requestTo(COMPLETIONS))
				.andExpect(jsonPath("$.max_tokens").value(1234))
				.andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));

		assertInstanceOf(ChatResult.Answer.class, client.complete("system", "user", null));
		openRouter.verify();
	}

	@Test
	void answerCutOffAtMaxTokensIsRejected() {
		openRouter.expect(requestTo(COMPLETIONS)).andRespond(withSuccess(CUT_OFF_ANSWER, MediaType.APPLICATION_JSON));

		assertInstanceOf(ChatResult.Rejected.class, client.complete("system", "user", null));
	}

	@Test
	void rateLimitIsBusyForRetryAfter() {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.RETRY_AFTER, "30");
		openRouter.expect(requestTo(COMPLETIONS)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

		ChatResult.Busy busy = assertInstanceOf(ChatResult.Busy.class, client.complete("system", "user", null));

		assertEquals(Duration.ofSeconds(30), busy.retryAfter());
	}

	@Test
	void tooLargeRequestIsRejected() {
		openRouter.expect(requestTo(COMPLETIONS)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

		assertInstanceOf(ChatResult.Rejected.class, client.complete("system", "user", null));
	}

	@Test
	void refusedKeyIsMisconfigured() {
		openRouter.expect(requestTo(COMPLETIONS)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertInstanceOf(ChatResult.Misconfigured.class, client.complete("system", "user", null));
	}

	@Test
	void missingKeyIsMisconfiguredWithoutCallingOpenRouter() {
		appProperties.getOpenRouter().setApiKey("");

		assertInstanceOf(ChatResult.Misconfigured.class, client.complete("system", "user", null));
		openRouter.verify();
	}

	@Test
	void serverErrorIsUnavailable() {
		openRouter.expect(requestTo(COMPLETIONS)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

		assertInstanceOf(ChatResult.Unavailable.class, client.complete("system", "user", null));
	}

	@Test
	void networkErrorIsUnavailable() {
		openRouter.expect(requestTo(COMPLETIONS)).andRespond(withException(new IOException("Connection reset")));

		assertInstanceOf(ChatResult.Unavailable.class, client.complete("system", "user", null));
	}

	@Test
	void answerWithoutContentIsUnavailable() {
		openRouter.expect(requestTo(COMPLETIONS)).andRespond(withSuccess("{\"choices\": []}", MediaType.APPLICATION_JSON));

		assertInstanceOf(ChatResult.Unavailable.class, client.complete("system", "user", null));
	}

	@Test
	void callBeyondTheConcurrencyLimitIsBusyAtOnce() throws Exception {
		appProperties.getOpenRouter().setMaxConcurrentRequests(1);
		client = newClient();
		CountDownLatch firstCallStarted = new CountDownLatch(1);
		CountDownLatch releaseFirstCall = new CountDownLatch(1);
		openRouter.expect(manyTimes(), requestTo(COMPLETIONS)).andRespond(request -> {
			firstCallStarted.countDown();
			try {
				releaseFirstCall.await();
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return withSuccess(ANSWER, MediaType.APPLICATION_JSON).createResponse(request);
		});

		CompletableFuture<ChatResult> first = CompletableFuture.supplyAsync(() -> client.complete("system", "user", null));
		firstCallStarted.await();
		ChatResult second = client.complete("system", "user", null);
		releaseFirstCall.countDown();

		assertInstanceOf(ChatResult.Busy.class, second);
		assertInstanceOf(ChatResult.Answer.class, first.get());
	}

	/** The vacancy import still relies on chat() throwing with OpenRouter's HTTP error as the cause. */
	@Test
	void chatKeepsItsOlderExceptionContract() {
		openRouter.expect(requestTo(COMPLETIONS)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> client.chat("system", "user", null));

		assertEquals(429, assertInstanceOf(RestClientResponseException.class, ex.getCause()).getStatusCode().value());
	}

	private OpenRouterClient newClient() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://openrouter");
		openRouter = MockRestServiceServer.bindTo(builder).build();
		return new OpenRouterClient(builder.build(), appProperties, JsonMapper.builder().build());
	}
}
