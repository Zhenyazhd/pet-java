package com.jobsearch.latexworker;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** Writes HTTP responses; every error body is {@code {"error": code, "message": text}}. */
final class Responses {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private Responses() {
	}

	static void json(HttpExchange exchange, int status, Map<String, String> body) throws IOException {
		send(exchange, status, "application/json", JSON.writeValueAsBytes(body));
	}

	static void error(HttpExchange exchange, int status, String code, String message) throws IOException {
		json(exchange, status, Map.of("error", code, "message", message));
	}

	static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
		exchange.getResponseHeaders().set("Content-Type", contentType);
		exchange.sendResponseHeaders(status, body.length);
		exchange.getResponseBody().write(body);
	}
}
