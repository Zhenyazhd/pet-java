package com.jobsearch.core_api.common;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain/validation failures to a uniform JSON error body for the frontend. */
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> fields = new HashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			fields.put(error.getField(), error.getDefaultMessage());
		}
		log.warn("Validation failed: {}", fields);
		return ResponseEntity.badRequest().body(errorBody(HttpStatus.BAD_REQUEST, "Validation failed", fields));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
		log.warn("Bad request: {}", ex.getMessage());
		return ResponseEntity.badRequest()
				.body(errorBody(HttpStatus.BAD_REQUEST, ex.getMessage(), null));
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
		// Used for upstream/runtime failures (OpenRouter, S3, LaTeX, missing config).
		log.error("Upstream/runtime failure: {}", ex.getMessage(), ex);
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
				.body(errorBody(HttpStatus.BAD_GATEWAY, ex.getMessage(), null));
	}

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
		log.info("Not found: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(errorBody(HttpStatus.NOT_FOUND, ex.getMessage(), null));
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
		log.warn("Conflict: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(errorBody(HttpStatus.CONFLICT, ex.getMessage(), null));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(errorBody(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", null));
	}

	private Map<String, Object> errorBody(HttpStatus status, String message, Map<String, String> fields) {
		Map<String, Object> body = new HashMap<>();
		body.put("timestamp", Instant.now().toString());
		body.put("status", status.value());
		body.put("error", status.getReasonPhrase());
		body.put("message", message);
		if (fields != null) {
			body.put("fields", fields);
		}
		return body;
	}
}
