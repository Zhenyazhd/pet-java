package com.jobsearch.core_api.common;

import java.time.Duration;

/** An upstream the request depends on cannot serve it now; mapped to 503, with Retry-After when known. */
public class ServiceUnavailableException extends RuntimeException {

	private final Duration retryAfter;

	public ServiceUnavailableException(String message, Duration retryAfter) {
		super(message);
		this.retryAfter = retryAfter;
	}

	/** When to try again, or null if unknown. */
	public Duration getRetryAfter() {
		return retryAfter;
	}
}
