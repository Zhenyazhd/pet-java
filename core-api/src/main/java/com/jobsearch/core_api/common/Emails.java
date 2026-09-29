package com.jobsearch.core_api.common;

import java.util.Locale;

/** Shared email normalization for auth/profile writes and lookups. */
public final class Emails {

	private Emails() {
	}

	/** Strip + lowercase ({@link Locale#ROOT}); {@code null} becomes empty string. */
	public static String normalize(String email) {
		return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
	}

	/** Local-part before {@code @}, or the whole string if {@code @} is missing. */
	public static String localPart(String email) {
		if (email == null || email.isEmpty()) {
			return "";
		}
		int at = email.indexOf('@');
		return at > 0 ? email.substring(0, at) : email;
	}
}
