package com.jobsearch.pdfworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Starts the real server on a free port and checks every status code from SPEC.md §3.2. */
class WorkerServerTest {

	private static final String TOKEN = "test-token";
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final HttpClient client = HttpClient.newHttpClient();
	private final AtomicBoolean ready = new AtomicBoolean(false);
	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void healthzAlwaysAnswers() throws Exception {
		startServer(5, 1000);

		assertEquals(200, send(HttpRequest.newBuilder(uri("/healthz"))).statusCode());
	}

	@Test
	void readyzFollowsWarmUpFlag() throws Exception {
		startServer(5, 1000);

		assertEquals(503, send(HttpRequest.newBuilder(uri("/readyz"))).statusCode());
		ready.set(true);
		assertEquals(200, send(HttpRequest.newBuilder(uri("/readyz"))).statusCode());
	}

	@Test
	void rejectsWrongMethodAndToken() throws Exception {
		startServer(5, 1000);

		assertEquals(405, send(HttpRequest.newBuilder(uri("/compile")).header("X-Worker-Token", TOKEN)).statusCode());
		assertEquals(401, send(post("{\"source\":\"x\"}").setHeader("X-Worker-Token", "wrong")).statusCode());
	}

	@Test
	void rejectsOversizedAndMalformedBodies() throws Exception {
		startServer(5, 100);

		assertEquals(413, send(post("{\"source\":\"" + "x".repeat(200) + "\"}")).statusCode());
		assertEquals(400, send(post("not json")).statusCode());
		assertEquals(400, send(post("{\"source\":\"  \"}")).statusCode());
	}

	@Test
	void mapsCompileResultsToStatuses() throws Exception {
		TestSupport.assumeTypstInstalled();
		startServer(2, 1024 * 1024);

		HttpResponse<byte[]> ok = send(post(sourceJson(TestSupport.warmupSource())));
		assertEquals(200, ok.statusCode());
		assertEquals("application/pdf", ok.headers().firstValue("Content-Type").orElseThrow());
		assertEquals("1", ok.headers().firstValue("X-Page-Count").orElseThrow());
		assertTrue(ok.headers().firstValue("X-Page-Fill").orElseThrow().matches("\\d\\.\\d\\d"));
		assertTrue(ok.headers().firstValue("X-Compile-Millis").isPresent());
		assertTrue(ok.headers().firstValue("X-Page-Scale").isEmpty());

		assertEquals(422, send(post(sourceJson(TestSupport.BROKEN_DOCUMENT))).statusCode());
		assertEquals(504, send(post(sourceJson(TestSupport.ENDLESS_DOCUMENT))).statusCode());
	}

	@Test
	void fitModeReturnsTheScaleHeader() throws Exception {
		TestSupport.assumeTypstInstalled();
		startServer(30, 1024 * 1024);

		HttpResponse<byte[]> up = send(post(sourceJson(TestSupport.scalableDocument(50), true)));
		assertEquals(200, up.statusCode());
		assertEquals("1", up.headers().firstValue("X-Page-Count").orElseThrow());
		assertTrue(Double.parseDouble(up.headers().firstValue("X-Page-Fill").orElseThrow()) >= 0.93);
		assertTrue(up.headers().firstValue("X-Page-Scale").orElseThrow().matches("1\\.\\d{3}"),
				up.headers().firstValue("X-Page-Scale").orElseThrow());

		HttpResponse<byte[]> down = send(post(sourceJson(TestSupport.scalableDocument(63), true)));
		assertEquals("1", down.headers().firstValue("X-Page-Count").orElseThrow());
		assertTrue(down.headers().firstValue("X-Page-Scale").orElseThrow().matches("0\\.\\d{3}"));

		HttpResponse<byte[]> hopeless = send(post(sourceJson(TestSupport.scalableDocument(90), true)));
		assertEquals("2", hopeless.headers().firstValue("X-Page-Count").orElseThrow());
		assertEquals("0.880", hopeless.headers().firstValue("X-Page-Scale").orElseThrow());

		HttpResponse<byte[]> fitsAlready = send(post(sourceJson(TestSupport.scalableDocument(56), true)));
		assertEquals("1.000", fitsAlready.headers().firstValue("X-Page-Scale").orElseThrow());

		HttpResponse<byte[]> noFit = send(post(sourceJson(TestSupport.scalableDocument(63), false)));
		assertEquals("2", noFit.headers().firstValue("X-Page-Count").orElseThrow());
		assertTrue(noFit.headers().firstValue("X-Page-Scale").isEmpty());

		assertEquals(422, send(post(sourceJson(TestSupport.BROKEN_DOCUMENT, true))).statusCode());
	}

	@Test
	void answersBusyWhenAllSlotsAreTaken() throws Exception {
		TestSupport.assumeTypstInstalled();
		startServer(3, 1024 * 1024);

		CompletableFuture<HttpResponse<byte[]>> slow = client.sendAsync(
				post(sourceJson(TestSupport.ENDLESS_DOCUMENT)).build(), HttpResponse.BodyHandlers.ofByteArray());
		Thread.sleep(500);
		HttpResponse<byte[]> busy = send(post(sourceJson(TestSupport.BROKEN_DOCUMENT)));

		assertEquals(503, busy.statusCode());
		assertEquals("1", busy.headers().firstValue("Retry-After").orElseThrow());
		assertEquals(504, slow.get().statusCode());
	}

	/** One compile slot, so a second concurrent request is always rejected as busy. */
	private void startServer(int timeoutSeconds, int maxSourceBytes) throws Exception {
		// Port 0 lets the OS pick a free port.
		WorkerConfig config = new WorkerConfig(0, TOKEN, 1, Duration.ofSeconds(timeoutSeconds),
				maxSourceBytes, 10 * 1024 * 1024, "typst");
		Compiler compiler = new Compiler(config.typstBin(), config.compileTimeout(), config.maxPdfBytes());
		server = WorkerMain.createServer(config, compiler, ready);
		server.start();
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + server.getAddress().getPort() + path);
	}

	private HttpRequest.Builder post(String body) {
		return HttpRequest.newBuilder(uri("/compile"))
				.header("X-Worker-Token", TOKEN)
				.POST(HttpRequest.BodyPublishers.ofString(body));
	}

	private HttpResponse<byte[]> send(HttpRequest.Builder request) throws Exception {
		return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
	}

	private static String sourceJson(String source) {
		return JSON.writeValueAsString(Map.of("source", source));
	}

	private static String sourceJson(String source, boolean fit) {
		return JSON.writeValueAsString(Map.of("source", source, "fit", fit));
	}
}
