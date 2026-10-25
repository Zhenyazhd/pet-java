package com.jobsearch.core_api.auth.google;

/** Google vouched for a person but they cannot be let in; {@code reason} is what the sign-in page is told. */
class GoogleSignInException extends RuntimeException {

	static final String UNVERIFIED = "unverified";
	static final String INVITE_REQUIRED = "invite_required";
	static final String INVITE_INVALID = "invite_invalid";
	static final String FAILED = "failed";

	private final String reason;

	GoogleSignInException(String reason) {
		super(reason);
		this.reason = reason;
	}

	String reason() {
		return reason;
	}
}
