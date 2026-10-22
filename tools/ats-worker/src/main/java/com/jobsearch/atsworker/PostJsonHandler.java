package com.jobsearch.atsworker;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * What every {@code POST} endpoint of the worker shares, in the order that keeps a bad request cheap: method, token,
 * body size, JSON, the endpoint's own validation (all before a slot is taken), then a slot or {@code 503 busy}.
 * The queue is core-api's, so a full worker says "busy" instead of making the caller wait.
 */
abstract class PostJsonHandler implements HttpHandler {

	/** The work of one request, run while a slot is held. */
	protected interface Work {
		void run() throws IOException;
	}

	private static final Logger log = Logger.getLogger(PostJsonHandler.class.getName());
	/** Requests being handled by any endpoint of this JVM, from the first byte read to the last written. */
	private static final AtomicInteger IN_FLIGHT = new AtomicInteger();

	private static final int IDLE_POLL_MILLIS = 100;

	private final String name;
	private final byte[] token;
	private final int maxBodyBytes;
	private final Semaphore slots;
	private final int retryAfterSeconds;

	PostJsonHandler(String name, String token, int maxBodyBytes, int concurrency, int retryAfterSeconds) {
		this.name = name;
		this.token = token.getBytes(StandardCharsets.UTF_8);
		this.maxBodyBytes = maxBodyBytes;
		this.slots = new Semaphore(concurrency);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	/**
	 * Waits until no request is being handled, for a shutdown that should not cut a model call in half.
	 *
	 * @return false when requests were still running after {@code timeout}
	 */
	static boolean awaitIdle(Duration timeout) throws InterruptedException {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (IN_FLIGHT.get() > 0) {
			if (System.nanoTime() - deadline > 0) {
				return false;
			}
			Thread.sleep(IDLE_POLL_MILLIS);
		}
		return true;
	}

	@Override
	public final void handle(HttpExchange exchange) throws IOException {
		IN_FLIGHT.incrementAndGet();
		try (exchange) {
			if (!"POST".equals(exchange.getRequestMethod())) {
				Responses.error(exchange, 405, "method_not_allowed", "Use POST");
				return;
			}
			if (!Responses.authorised(exchange, token)) {
				return;
			}
			if (!preflight(exchange)) {
				return;
			}
			byte[] body = exchange.getRequestBody().readNBytes(maxBodyBytes + 1);
			if (body.length > maxBodyBytes) {
				Responses.error(exchange, 413, "too_large", "Request body exceeds " + maxBodyBytes + " bytes");
				return;
			}
			JsonNode json;
			try {
				json = Responses.JSON.readTree(body);
			}
			catch (JacksonException ex) {
				badRequest(exchange);
				return;
			}
			Work work = prepare(exchange, json, body.length);
			if (work == null) {
				return;
			}
			if (!slots.tryAcquire()) {
				log.info(name + " rejected reason=busy");
				exchange.getResponseHeaders().set("Retry-After", String.valueOf(retryAfterSeconds));
				Responses.error(exchange, 503, "busy", "All " + name + " slots are busy");
				return;
			}
			try {
				work.run();
			}
			finally {
				slots.release();
			}
		}
		finally {
			IN_FLIGHT.decrementAndGet();
		}
	}

	/** Runs after the token check and before the body is read; false means the answer is already written. */
	protected boolean preflight(HttpExchange exchange) throws IOException {
		return true;
	}

	/** Validates the request; returns the work to do in a slot, or null after writing the error itself. */
	protected abstract Work prepare(HttpExchange exchange, JsonNode json, int bodyBytes) throws IOException;

	/** What a valid request looks like, for the 400 answer. */
	protected abstract String usage();

	protected final void badRequest(HttpExchange exchange) throws IOException {
		Responses.error(exchange, 400, "bad_request", usage());
	}
}
