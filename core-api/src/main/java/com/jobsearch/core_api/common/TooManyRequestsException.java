package com.jobsearch.core_api.common;

import java.time.Duration;

/** The caller used up an allowance (e.g. a daily quota of an upstream); mapped to 429, with Retry-After when known. */
public class TooManyRequestsException extends RuntimeException {

	private final Duration retryAfter;

	public TooManyRequestsException(String message, Duration retryAfter) {
		super(message);
		this.retryAfter = retryAfter;
	}

	/** When to try again, or null if unknown. */
	public Duration getRetryAfter() {
		return retryAfter;
	}
}
