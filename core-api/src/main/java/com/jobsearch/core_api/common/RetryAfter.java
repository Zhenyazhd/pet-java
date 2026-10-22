package com.jobsearch.core_api.common;

/** Reads a worker's {@code Retry-After} header (seconds). */
public final class RetryAfter {

	private RetryAfter() {
	}

	/** At least 1 second; {@code fallback} when the header is missing or not a number. */
	public static long seconds(String header, long fallback) {
		try {
			return header == null ? fallback : Math.max(1, Long.parseLong(header.strip()));
		}
		catch (NumberFormatException ex) {
			return fallback;
		}
	}
}
