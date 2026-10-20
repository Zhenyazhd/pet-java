package com.jobsearch.pdfworker;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code POST /compile}: {@code {"source": "<Typst>", "fit": false}} in, PDF out (see SPEC.md §3.2).
 *
 * <p>When every compile slot is busy the request is rejected with 503 instead of waiting:
 * the queue lives in core-api, which retries later.
 */
final class CompileHandler implements HttpHandler {

	private static final Logger log = Logger.getLogger(CompileHandler.class.getName());
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final Compiler compiler;
	private final byte[] token;
	private final int maxSourceBytes;
	private final Semaphore slots;

	CompileHandler(Compiler compiler, WorkerConfig config) {
		this.compiler = compiler;
		this.token = config.token().getBytes(StandardCharsets.UTF_8);
		this.maxSourceBytes = config.maxSourceBytes();
		this.slots = new Semaphore(config.concurrency());
	}

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		try (exchange) {
			if (!"POST".equals(exchange.getRequestMethod())) {
				Responses.error(exchange, 405, "method_not_allowed", "Use POST");
				return;
			}
			String presented = exchange.getRequestHeaders().getFirst("X-Worker-Token");
			if (presented == null || !MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8))) {
				Responses.error(exchange, 401, "unauthorized", "Missing or invalid X-Worker-Token");
				return;
			}
			byte[] body = exchange.getRequestBody().readNBytes(maxSourceBytes + 1);
			if (body.length > maxSourceBytes) {
				Responses.error(exchange, 413, "too_large", "Request body exceeds " + maxSourceBytes + " bytes");
				return;
			}
			JsonNode request = readRequest(body);
			String source = request.path("source").asString("");
			boolean fit = request.path("fit").asBoolean(false);
			if (source.isBlank()) {
				Responses.error(exchange, 400, "bad_request", "Expected JSON {\"source\": \"<Typst>\"}");
				return;
			}
			if (!slots.tryAcquire()) {
				log.info("compile rejected reason=busy");
				exchange.getResponseHeaders().set("Retry-After", "1");
				Responses.error(exchange, 503, "busy", "All compile slots are busy");
				return;
			}
			try {
				compileAndRespond(exchange, source, fit, body.length);
			}
			finally {
				slots.release();
			}
		}
	}

	private void compileAndRespond(HttpExchange exchange, String source, boolean fit, int sourceBytes) throws IOException {
		long started = System.nanoTime();
		CompileResult result;
		try {
			result = compiler.compile(source, fit);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			respondInternalError(exchange, ex);
			return;
		}
		catch (IOException | RuntimeException ex) {
			respondInternalError(exchange, ex);
			return;
		}
		long millis = (System.nanoTime() - started) / 1_000_000;

		switch (result) {
			case CompileResult.Ok(byte[] pdf, PageMetrics metrics, Double scale) -> {
				log.info("compile ok millis=" + millis + " sourceBytes=" + sourceBytes + " pdfBytes=" + pdf.length
						+ (metrics == null ? " metrics=none" : " pages=" + metrics.pages() + " fill=" + String.format(Locale.ROOT, "%.2f", metrics.fill()))
						+ (scale == null ? "" : " scale=" + formatScale(scale)));
				exchange.getResponseHeaders().set("X-Compile-Millis", String.valueOf(millis));
				if (metrics != null) {
					exchange.getResponseHeaders().set("X-Page-Count", String.valueOf(metrics.pages()));
					exchange.getResponseHeaders().set("X-Page-Fill", formatTwoDecimals(metrics.fill()));
				}
				if (scale != null) {
					exchange.getResponseHeaders().set("X-Page-Scale", formatScale(scale));
				}
				Responses.send(exchange, 200, "application/pdf", pdf);
			}
			case CompileResult.Failed(String compileLog) -> {
				log.info("compile failed code=compile_error millis=" + millis);
				Responses.error(exchange, 422, "compile_error", compileLog);
			}
			case CompileResult.TimedOut() -> {
				log.info("compile failed code=timeout millis=" + millis);
				Responses.error(exchange, 504, "timeout", "Compilation exceeded the time limit");
			}
		}
	}

	private static void respondInternalError(HttpExchange exchange, Exception ex) throws IOException {
		log.log(Level.SEVERE, "compile error", ex);
		Responses.error(exchange, 500, "internal", "Compilation failed due to a worker error");
	}

	/** Three decimals, the precision the scale is searched at. */
	private static String formatScale(double scale) {
		return String.format(Locale.ROOT, "%.3f", scale);
	}

	private static String formatTwoDecimals(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	/** Parses the request body; anything that is not a JSON object yields an empty object (no source -> 400). */
	private static JsonNode readRequest(byte[] body) {
		try {
			JsonNode node = JSON.readTree(body);
			return node.isObject() ? node : JSON.createObjectNode();
		}
		catch (JacksonException ex) {
			return JSON.createObjectNode();
		}
	}
}
