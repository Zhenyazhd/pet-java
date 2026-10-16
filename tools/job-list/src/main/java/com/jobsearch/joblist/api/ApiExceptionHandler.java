package com.jobsearch.joblist.api;

import com.jobsearch.joblist.common.BadRequestException;
import com.jobsearch.joblist.common.NotFoundException;
import com.jobsearch.joblist.common.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Every error leaves as an {@link ApiError}: our own exceptions, the framework's 4xx (bad JSON, wrong method...) and the rest as 500. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(BadRequestException.class)
	ResponseEntity<Object> badRequest(BadRequestException ex) {
		return body(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(NotFoundException.class)
	ResponseEntity<Object> notFound(NotFoundException ex) {
		return body(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(TooManyRequestsException.class)
	ResponseEntity<Object> tooMany(TooManyRequestsException ex) {
		return body(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<Object> wrongType(MethodArgumentTypeMismatchException ex) {
		return body(HttpStatus.BAD_REQUEST, "Parameter '" + ex.getName() + "' has the wrong type");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		FieldError first = ex.getBindingResult().getFieldErrors().getFirst();
		return body(HttpStatus.BAD_REQUEST, first.getField() + ": " + first.getDefaultMessage());
	}

	/** The framework's own client errors (unreadable body, wrong method, unknown path, missing parameter). */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(
			Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
		HttpStatus status = HttpStatus.valueOf(statusCode.value());
		String message = body instanceof ProblemDetail problem && problem.getDetail() != null ? problem.getDetail() : status.getReasonPhrase();
		return ResponseEntity.status(status).headers(headers).body(new ApiError(status.value(), status.getReasonPhrase(), message));
	}

	/** Anything unexpected: the caller gets a plain message, the detail stays in the log. */
	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> unexpected(Exception ex) {
		log.error("Unhandled error", ex);
		return body(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
	}

	private static ResponseEntity<Object> body(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(new ApiError(status.value(), status.getReasonPhrase(), message));
	}
}
