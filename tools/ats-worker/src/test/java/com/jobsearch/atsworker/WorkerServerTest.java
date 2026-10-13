package com.jobsearch.atsworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class WorkerServerTest {

	private static final String TOKEN = "test-token";

	private HttpServer server;
	private HttpClient client;
	private URI base;

	@BeforeEach
	void start() throws IOException {
		WorkerConfig config = WorkerConfig.fromEnv(Map.of("WORKER_TOKEN", TOKEN, "WORKER_PORT", "1", "MAX_REQUEST_BYTES", "200000"));
		server = WorkerMain.createServer(new WorkerConfig(0, TOKEN, 2, Duration.ofSeconds(20), config.maxRequestBytes(), 10, Duration.ofSeconds(1), config.llm()));
		server.start();
		base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
		client = HttpClient.newHttpClient();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private HttpResponse<String> post(String token, String body) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve("/check"))
				.POST(HttpRequest.BodyPublishers.ofString(body));
		if (token != null) {
			request.header("X-Worker-Token", token);
		}
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static String json(byte[] pdf, String expected) {
		return Responses.JSON.writeValueAsString(Map.of(
				"pdfBase64", Base64.getEncoder().encodeToString(pdf), "expected", expected));
	}

	@Test
	void aCheckReturnsTheReport() throws Exception {
		HttpResponse<String> response = post(TOKEN, json(TestPdf.singleColumn(TestPdf.RESUME), TestPdf.RESUME));

		assertEquals(200, response.statusCode());
		JsonNode report = Responses.JSON.readTree(response.body());
		assertEquals("ok", report.path("verdict").asString());
		assertTrue(report.path("text").asString().contains("payment gateway"));
	}

	@Test
	void aMissingOrWrongTokenIsRefused() throws Exception {
		String body = json(TestPdf.singleColumn(TestPdf.RESUME), "");

		assertEquals(401, post(null, body).statusCode());
		assertEquals(401, post("wrong", body).statusCode());
	}

	@Test
	void garbageInsteadOfJsonIsABadRequest() throws Exception {
		assertEquals(400, post(TOKEN, "not json").statusCode());
		assertEquals(400, post(TOKEN, "{\"pdfBase64\": \"***\"}").statusCode());
	}

	@Test
	void somethingThatIsNotAPdfIsRefused() throws Exception {
		HttpResponse<String> response = post(TOKEN, json("hello world".getBytes(), ""));

		assertEquals(422, response.statusCode());
		assertEquals("not_a_pdf", Responses.JSON.readTree(response.body()).path("error").asString());
	}

	@Test
	void aBrokenPdfIsUnreadableNotAServerError() throws Exception {
		HttpResponse<String> response = post(TOKEN, json("%PDF-1.7 broken".getBytes(), ""));

		assertEquals(422, response.statusCode());
		assertEquals("unreadable_pdf", Responses.JSON.readTree(response.body()).path("error").asString());
	}

	@Test
	void anOversizedBodyIsRefused() throws Exception {
		assertEquals(413, post(TOKEN, "x".repeat(200_001)).statusCode());
	}

	@Test
	void readinessTurnsToServiceUnavailableWhileDraining() throws Exception {
		java.util.concurrent.atomic.AtomicBoolean draining = new java.util.concurrent.atomic.AtomicBoolean();
		HttpServer other = WorkerMain.createServer(new WorkerConfig(0, TOKEN, 2, Duration.ofSeconds(20), 200000, 10,
				Duration.ofSeconds(1), WorkerConfig.fromEnv(Map.of("WORKER_TOKEN", TOKEN)).llm()), draining);
		other.start();
		try {
			URI ready = URI.create("http://127.0.0.1:" + other.getAddress().getPort() + "/readyz");
			assertEquals(200, client.send(HttpRequest.newBuilder(ready).build(), HttpResponse.BodyHandlers.ofString()).statusCode());

			draining.set(true);

			assertEquals(503, client.send(HttpRequest.newBuilder(ready).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
		}
		finally {
			other.stop(0);
		}
	}

	@Test
	void anIssueSeverityIsWrittenInLowerCase() throws Exception {
		HttpResponse<String> response = post(TOKEN, json(TestPdf.withoutText(), ""));

		assertEquals(200, response.statusCode(), response.body());
		assertEquals("error", Responses.JSON.readTree(response.body()).path("issues").path(0).path("severity").asString());
	}

	@Test
	void healthEndpointsAnswer() throws Exception {
		for (String path : new String[] {"/healthz", "/readyz"}) {
			HttpResponse<String> response = client.send(HttpRequest.newBuilder(base.resolve(path)).build(),
					HttpResponse.BodyHandlers.ofString());
			assertEquals(200, response.statusCode());
		}
	}
}
