package com.jobsearch.core_api.vacancy;

import java.text.Normalizer;
import java.util.Locale;

/**
 * The one definition of "the same skill": Unicode-normalised (NFKC, so a non-breaking space is a space),
 * whitespace collapsed and trimmed, lower case. V3__skill_dictionary.sql applies the same steps in SQL.
 */
final class SkillKey {

	private static final String WHITESPACE = "[\\s\\p{Z}]+";

	private SkillKey() {
	}

	static String display(String name) {
		return Normalizer.normalize(name, Normalizer.Form.NFKC).replaceAll(WHITESPACE, " ").strip();
	}

	static String of(String name) {
		return display(name).toLowerCase(Locale.ROOT);
	}
}
