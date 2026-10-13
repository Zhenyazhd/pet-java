package com.jobsearch.atsworker;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** Writes HTTP responses; every error body is {@code {"error": code, "message": text}}. */
final class Responses {

	static final JsonMapper JSON = JsonMapper.builder().build();

	private Responses() {
	}

	static void json(HttpExchange exchange, int status, Object body) throws IOException {
		send(exchange, status, "application/json", JSON.writeValueAsBytes(body));
	}

	static void error(HttpExchange exchange, int status, String code, String message) throws IOException {
		json(exchange, status, Map.of("error", code, "message", message));
	}

	/** True when the caller sent the right token; otherwise the 401 is already written. */
	static boolean authorised(HttpExchange exchange, byte[] token) throws IOException {
		String presented = exchange.getRequestHeaders().getFirst("X-Worker-Token");
		if (presented == null || !MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8))) {
			error(exchange, 401, "unauthorized", "Missing or invalid X-Worker-Token");
			return false;
		}
		return true;
	}

	static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
		exchange.getResponseHeaders().set("Content-Type", contentType);
		exchange.sendResponseHeaders(status, body.length);
		exchange.getResponseBody().write(body);
	}
}
