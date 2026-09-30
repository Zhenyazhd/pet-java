package com.jobsearch.core_api.common;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Single JSON error shape for {@code @RestControllerAdvice} and servlet/security filters.
 * Body: {@code timestamp, status, error, message[, fields]}.
 */
@Component
public class ApiErrorResponses {

	private final ObjectMapper objectMapper;

	public ApiErrorResponses(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public Map<String, Object> body(HttpStatus status, String message) {
		return body(status, message, null);
	}

	public Map<String, Object> body(HttpStatus status, String message, Map<String, String> fields) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("timestamp", Instant.now().toString());
		body.put("status", status.value());
		body.put("error", status.getReasonPhrase());
		body.put("message", message == null ? status.getReasonPhrase() : message);
		if (fields != null && !fields.isEmpty()) {
			body.put("fields", fields);
		}
		return body;
	}

	public void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
		write(response, status, message, null);
	}

	public void write(
			HttpServletResponse response,
			HttpStatus status,
			String message,
			Map<String, String> extraHeaders
	) throws IOException {
		response.setStatus(status.value());
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		if (extraHeaders != null) {
			extraHeaders.forEach(response::setHeader);
		}
		objectMapper.writeValue(response.getWriter(), body(status, message));
	}
}
