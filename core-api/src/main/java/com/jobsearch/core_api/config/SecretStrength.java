package com.jobsearch.core_api.config;

import java.util.Locale;
import java.util.Set;

public final class SecretStrength {

	public static final int MIN_LENGTH = 12;
	private static final Set<String> PLACEHOLDERS = Set.of("changeme", "change-me", "password", "admin", "secret", "job_search");

	private SecretStrength() {
	}

	public static boolean isWeak(String secret) {
		if (secret == null || secret.length() < MIN_LENGTH) {
			return true;
		}
		String lower = secret.toLowerCase(Locale.ROOT);
		// "changeme1234" is the placeholder with digits added, and "aaaaaaaaaaaa" is long but not a secret.
		return PLACEHOLDERS.stream().anyMatch(lower::startsWith) || lower.chars().distinct().count() < 4;
	}

	public static void require(String name, String secret) {
		if (isWeak(secret)) {
			throw new IllegalStateException(name + " must be at least " + MIN_LENGTH
					+ " characters and not a placeholder such as 'changeme'");
		}
	}
}
