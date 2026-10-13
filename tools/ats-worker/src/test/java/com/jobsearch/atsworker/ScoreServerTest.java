package com.jobsearch.atsworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** {@code POST /score} against a fake OpenRouter. */
class ScoreServerTest {

	private static final String TOKEN = "test-token";
	private static final String SIX = """
			{"results": [{"system":"Workday","overallScore":70},{"system":"Taleo","overallScore":50},
			{"system":"iCIMS","overallScore":75},{"system":"Greenhouse","overallScore":65},
			{"system":"Lever","overallScore":60},{"system":"SuccessFactors","overallScore":68}]}""";

	private HttpServer fakeLlm;
	private HttpServer worker;
	private HttpClient client;
	private final AtomicReference<String> llmRequest = new AtomicReference<>();
	private final AtomicReference<String> llmAnswer = new AtomicReference<>();
	private final AtomicInteger llmStatus = new AtomicInteger(200);
	private final AtomicReference<String> llmAuth = new AtomicReference<>();
	private final java.util.concurrent.atomic.AtomicInteger llmDelayMillis = new java.util.concurrent.atomic.AtomicInteger();
	private final java.util.concurrent.atomic.AtomicBoolean llmDrips = new java.util.concurrent.atomic.AtomicBoolean();

	@BeforeEach
	void start() throws IOException {
		fakeLlm = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		fakeLlm.createContext("/chat/completions", exchange -> {
			try (exchange) {
				llmRequest.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
				llmAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
				if (llmDrips.get()) {
					// headers at once, then the body one byte and a long pause: HttpRequest.timeout alone never fires
					exchange.sendResponseHeaders(200, 1000);
					exchange.getResponseBody().write('{');
					exchange.getResponseBody().flush();
					sleep(8000);
					return;
				}
				sleep(llmDelayMillis.get());
				byte[] body = Responses.JSON.writeValueAsBytes(java.util.Map.of("choices",
						java.util.List.of(java.util.Map.of("message", java.util.Map.of("content", llmAnswer.get())))));
				Responses.send(exchange, llmStatus.get(), "application/json", body);
			}
		});
		fakeLlm.start();
		client = HttpClient.newHttpClient();
		llmAnswer.set(SIX);
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private void startWorker(String apiKey) throws IOException {
		startWorker(apiKey, Duration.ofSeconds(5));
	}

	private void startWorker(String apiKey, Duration modelTimeout) throws IOException {
		startWorker(apiKey, modelTimeout, 2);
	}

	private void startWorker(String apiKey, Duration modelTimeout, int slots) throws IOException {
		LlmConfig llm = new LlmConfig(apiKey, "http://127.0.0.1:" + fakeLlm.getAddress().getPort(), "test/model",
				modelTimeout, slots, 1000);
		worker = WorkerMain.createServer(new WorkerConfig(0, TOKEN, 2, Duration.ofSeconds(20), 1_000_000, 10, Duration.ofSeconds(1), llm));
		worker.start();
	}

	@AfterEach
	void stop() {
		fakeLlm.stop(0);
		if (worker != null) {
			worker.stop(0);
		}
	}

	private HttpResponse<String> score(String token, String body) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + worker.getAddress().getPort() + "/score"))
				.POST(HttpRequest.BodyPublishers.ofString(body));
		if (token != null) {
			request.header("X-Worker-Token", token);
		}
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static String code(HttpResponse<String> response) {
		return Responses.JSON.readTree(response.body()).path("error").asString();
	}

	@Test
	void theModelsSixScoresComeBackWithTheModelName() throws Exception {
		startWorker("key-1");

		HttpResponse<String> response = score(TOKEN, "{\"resumeText\":\"Jane Doe\",\"jobDescription\":\"Kubernetes\"}");

		assertEquals(200, response.statusCode(), response.body());
		JsonNode report = Responses.JSON.readTree(response.body());
		assertEquals(6, report.path("results").size());
		assertEquals("test/model", report.path("_model").asString());
		// what was sent to the provider
		JsonNode sent = Responses.JSON.readTree(llmRequest.get());
		assertEquals("test/model", sent.path("model").asString());
		assertEquals("json_object", sent.path("response_format").path("type").asString());
		assertTrue(sent.path("messages").path(0).path("content").asString().contains("MODE: targeted scoring"));
		assertEquals("Bearer key-1", llmAuth.get());
	}

	@Test
	void aPdfIsScoredByTheTextAnAtsReadsFromIt() throws Exception {
		startWorker("key");
		String body = Responses.JSON.writeValueAsString(java.util.Map.of(
				"pdfBase64", java.util.Base64.getEncoder().encodeToString(TestPdf.singleColumn(TestPdf.RESUME)),
				"jobDescription", "Kubernetes"));

		HttpResponse<String> response = score(TOKEN, body);

		assertEquals(200, response.statusCode(), response.body());
		assertEquals("pdf", Responses.JSON.readTree(response.body()).path("_resumeSource").asString());
		String prompt = Responses.JSON.readTree(llmRequest.get()).path("messages").path(0).path("content").asString();
		assertTrue(prompt.contains("Reduced the p99 latency of the checkout API"), "text extracted from the PDF");
		assertTrue(prompt.contains("Kubernetes"));
	}

	@Test
	void aPdfWithoutTextOrWhichIsNotAPdfCannotBeScored() throws Exception {
		startWorker("key");
		String empty = Responses.JSON.writeValueAsString(java.util.Map.of(
				"pdfBase64", java.util.Base64.getEncoder().encodeToString(TestPdf.withoutText())));
		String notPdf = Responses.JSON.writeValueAsString(java.util.Map.of(
				"pdfBase64", java.util.Base64.getEncoder().encodeToString("hello".getBytes())));

		assertEquals("no_text_layer", code(score(TOKEN, empty)));
		assertEquals("not_a_pdf", code(score(TOKEN, notPdf)));
		assertEquals(null, llmRequest.get(), "the model is not called for a PDF that cannot be read");
	}

	@Test
	void textAroundTheJsonIsDropped() throws Exception {
		startWorker("key");
		llmAnswer.set("Here is the result:\n```json\n" + SIX + "\n```\nHope it helps.");

		assertEquals(200, score(TOKEN, "{\"resumeText\":\"Jane\"}").statusCode());
	}

	@Test
	void aProviderThatDripsTheAnswerIsCutOffAtTheTimeout() throws Exception {
		startWorker("key", Duration.ofSeconds(1));
		llmDrips.set(true);

		long started = System.nanoTime();
		HttpResponse<String> response = score(TOKEN, "{\"resumeText\":\"Jane\"}");
		long seconds = (System.nanoTime() - started) / 1_000_000_000;

		assertEquals(504, response.statusCode());
		assertEquals("timeout", code(response));
		assertTrue(seconds < 5, "answered after " + seconds + "s, the provider was still dripping");
	}

	@Test
	void whenEveryScoringSlotIsTakenTheNextRequestIsToldToComeBack() throws Exception {
		startWorker("key", Duration.ofSeconds(5), 1);
		llmDelayMillis.set(1500);
		var first = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
			try {
				return score(TOKEN, "{\"resumeText\":\"Jane\"}");
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		});
		Thread.sleep(500);

		HttpResponse<String> second = score(TOKEN, "{\"resumeText\":\"Jane\"}");

		assertEquals(503, second.statusCode());
		assertEquals("busy", code(second));
		assertEquals("2", second.headers().firstValue("Retry-After").orElse(""));
		assertEquals(200, first.get().statusCode(), "the call that held the slot still completes");
		// and the slot is free again afterwards
		llmDelayMillis.set(0);
		assertEquals(200, score(TOKEN, "{\"resumeText\":\"Jane\"}").statusCode());
	}

	@Test
	void shutdownWaitsForARequestInFlightAndNotForAnIdleWorker() throws Exception {
		startWorker("key");
		assertTrue(PostJsonHandler.awaitIdle(Duration.ofSeconds(1)), "an idle worker is idle at once");
		llmDelayMillis.set(1500);
		var inFlight = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
			try {
				return score(TOKEN, "{\"resumeText\":\"Jane\"}");
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		});
		Thread.sleep(400);

		assertFalse(PostJsonHandler.awaitIdle(Duration.ofMillis(300)), "a request is still running");
		assertTrue(PostJsonHandler.awaitIdle(Duration.ofSeconds(5)), "it finishes within the grace period");
		assertEquals(200, inFlight.get().statusCode());
	}

	@Test
	void anAnswerWithoutSixResultsIsBad() throws Exception {
		startWorker("key");
		llmAnswer.set("{\"results\": [{\"system\":\"Workday\"}]}");

		HttpResponse<String> response = score(TOKEN, "{\"resumeText\":\"Jane\"}");

		assertEquals(502, response.statusCode());
		assertEquals("bad_model_answer", code(response));
	}

	@Test
	void anAnswerThatIsNotJsonIsBad() throws Exception {
		startWorker("key");
		llmAnswer.set("I cannot do that.");

		assertEquals("bad_model_answer", code(score(TOKEN, "{\"resumeText\":\"Jane\"}")));
	}

	@Test
	void aProviderErrorIsABadGatewayAndNeverLeaksTheKey() throws Exception {
		startWorker("secret-key");
		llmStatus.set(500);

		HttpResponse<String> response = score(TOKEN, "{\"resumeText\":\"Jane\"}");

		assertEquals(502, response.statusCode());
		assertEquals("llm_error", code(response));
		assertTrue(!response.body().contains("secret-key"));
	}

	@Test
	void withoutAnApiKeyScoringIsNotConfigured() throws Exception {
		startWorker("");

		HttpResponse<String> response = score(TOKEN, "{\"resumeText\":\"Jane\"}");

		assertEquals(503, response.statusCode());
		assertEquals("not_configured", code(response));
	}

	@Test
	void tokenAndBodyAreChecked() throws Exception {
		startWorker("key");

		assertEquals(401, score(null, "{\"resumeText\":\"Jane\"}").statusCode());
		assertEquals(401, score("wrong", "{\"resumeText\":\"Jane\"}").statusCode());
		assertEquals(400, score(TOKEN, "not json").statusCode());
		assertEquals(400, score(TOKEN, "{\"resumeText\":\"  \"}").statusCode());
		assertEquals(413, score(TOKEN, "{\"resumeText\":\"" + "x".repeat(300_000) + "\"}").statusCode());
		assertEquals(413, score(TOKEN, "x".repeat(1_000_001)).statusCode());
	}
}
