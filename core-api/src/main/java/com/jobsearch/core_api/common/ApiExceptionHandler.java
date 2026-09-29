package com.jobsearch.core_api.common;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

	private final ApiErrorResponses apiErrorResponses;

	public ApiExceptionHandler(ApiErrorResponses apiErrorResponses) {
		this.apiErrorResponses = apiErrorResponses;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> fields = new HashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			fields.put(error.getField(), error.getDefaultMessage());
		}
		log.warn("Validation failed: {}", fields);
		return ResponseEntity.badRequest()
				.body(apiErrorResponses.body(HttpStatus.BAD_REQUEST, "Validation failed", fields));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
		Map<String, String> fields = ex.getConstraintViolations().stream()
				.collect(Collectors.toMap(
						v -> v.getPropertyPath().toString(),
						v -> v.getMessage() == null ? "invalid" : v.getMessage(),
						(a, b) -> a
				));
		log.warn("Constraint validation failed: {}", fields);
		return ResponseEntity.badRequest()
				.body(apiErrorResponses.body(HttpStatus.BAD_REQUEST, "Validation failed", fields));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
		log.warn("Bad request: {}", ex.getMessage());
		return ResponseEntity.badRequest()
				.body(apiErrorResponses.body(HttpStatus.BAD_REQUEST, ex.getMessage()));
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
		// Used for upstream/runtime failures (OpenRouter, S3, LaTeX, missing config).
		log.error("Upstream/runtime failure: {}", ex.getMessage(), ex);
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
				.body(apiErrorResponses.body(HttpStatus.BAD_GATEWAY, ex.getMessage()));
	}

	@ExceptionHandler(UnauthorizedException.class)
	public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex) {
		log.info("Unauthorized: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(apiErrorResponses.body(HttpStatus.UNAUTHORIZED, ex.getMessage()));
	}

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
		log.info("Not found: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(apiErrorResponses.body(HttpStatus.NOT_FOUND, ex.getMessage()));
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
		if (ex.getCause() != null) {
			log.warn("Conflict: {}", ex.getMessage(), ex);
		}
		else {
			log.warn("Conflict: {}", ex.getMessage());
		}
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(apiErrorResponses.body(HttpStatus.CONFLICT, ex.getMessage()));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
		// Expected unique races should already be ConflictException via UniqueConstraint.
		// Do not map every integrity failure to 409 — CHECK/FK need diagnostics, not a fake conflict.
		String detail = ex.getMostSpecificCause().getMessage();
		String lower = detail == null ? "" : detail.toLowerCase();
		if (lower.contains("check constraint") || lower.contains("violates check")) {
			log.warn("Check constraint violated: {}", detail);
			return ResponseEntity.badRequest()
					.body(apiErrorResponses.body(HttpStatus.BAD_REQUEST, "Invalid data"));
		}
		log.error("Unhandled data integrity violation: {}", detail, ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(apiErrorResponses.body(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(apiErrorResponses.body(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"));
	}
}
