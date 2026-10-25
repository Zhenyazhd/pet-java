package com.jobsearch.core_api.common;

/**
 * Not allowed, or not yet allowed in (an address not confirmed). The {@code code} goes into the error body, so a client
 * can tell this refusal from a stale CSRF token, which is also a 403, without reading the message.
 */
public class ForbiddenException extends RuntimeException {

	public static final String EMAIL_NOT_VERIFIED = "email_not_verified";

	private final String code;

	public ForbiddenException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String code() {
		return code;
	}
}
