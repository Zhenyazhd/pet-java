package com.jobsearch.core_api.common;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.WebUtils;


@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	private final ApiErrorResponses apiErrorResponses;

	public ApiExceptionHandler(ApiErrorResponses apiErrorResponses) {
		this.apiErrorResponses = apiErrorResponses;
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> fields = new HashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			fields.put(error.getField(), error.getDefaultMessage());
		}
		log.warn("Validation failed: {}", fields);
		return ResponseEntity.badRequest().headers(headers)
				.body(apiErrorResponses.body(HttpStatus.BAD_REQUEST, "Validation failed", fields));
	}

	/** Renders every framework-handled exception (4xx and framework 5xx) in the shared error shape. */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(
			Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
		HttpStatus status = HttpStatus.resolve(statusCode.value());
		if (status == null) {
			status = statusCode.is4xxClientError() ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
		}
		// Most framework handlers pass a null body; the exception itself carries the detail.
		if (body == null && ex instanceof ErrorResponse errorResponse) {
			body = errorResponse.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
		}
		String message = body instanceof ProblemDetail problem && problem.getDetail() != null
				? problem.getDetail()
				: status.getReasonPhrase();
		String where = request == null ? "" : request.getDescription(false);
		if (status.is5xxServerError()) {
			log.error("Framework error {} {}", status.value(), where, ex);
			message = status.getReasonPhrase();
			if (request != null) {
				request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, ex, WebRequest.SCOPE_REQUEST);
			}
		}
		else {
			log.warn("Client error {} {}: {}", status.value(), where, ex.getMessage());
		}
		return ResponseEntity.status(status).headers(headers).contentType(MediaType.APPLICATION_JSON)
				.body(apiErrorResponses.body(status, message));
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

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
		log.warn("Bad request: {}", ex.getMessage());
		return ResponseEntity.badRequest()
				.body(apiErrorResponses.body(HttpStatus.BAD_REQUEST, ex.getMessage()));
	}

	@ExceptionHandler(ServiceUnavailableException.class)
	public ResponseEntity<Map<String, Object>> handleServiceUnavailable(ServiceUnavailableException ex) {
		log.warn("Service unavailable: {}", ex.getMessage());
		ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE);
		if (ex.getRetryAfter() != null) {
			response.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfter().toSeconds()));
		}
		return response.body(apiErrorResponses.body(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage()));
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
