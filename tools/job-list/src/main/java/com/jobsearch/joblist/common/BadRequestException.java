package com.jobsearch.joblist.common;

/** The request is wrong; the message is written for the caller. */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}
}
