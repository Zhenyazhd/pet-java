package com.jobsearch.joblist.common;

/** The caller asked for more paid work than it is allowed in a day; mapped to 429. */
public class TooManyRequestsException extends RuntimeException {

	public TooManyRequestsException(String message) {
		super(message);
	}
}
