package com.jobsearch.joblist.common;

/** The request is fine but cannot be done in the current state; the message is written for the caller. */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}
}
