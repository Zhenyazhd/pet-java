package com.jobsearch.atsworker;

/** A request that cannot be checked; carries the HTTP status and error code to answer with. */
final class CheckFailure extends RuntimeException {

	private final int status;
	private final String code;

	CheckFailure(int status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	int status() {
		return status;
	}

	String code() {
		return code;
	}
}
