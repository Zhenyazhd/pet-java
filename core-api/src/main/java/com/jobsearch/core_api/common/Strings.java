package com.jobsearch.core_api.common;

/** Small string helpers shared by services. */
public final class Strings {

	private Strings() {
	}

	/** Trimmed value, or {@code null} when blank. */
	public static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	/** Stripped value cut to {@code max} chars (with "…"), or {@code "(empty)"} when blank. */
	public static String abbreviate(String value, int max) {
		if (value == null || value.isBlank()) {
			return "(empty)";
		}
		String trimmed = value.strip();
		return trimmed.length() > max ? trimmed.substring(0, max) + "…" : trimmed;
	}
}
