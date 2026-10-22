package com.jobsearch.atsworker;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Turns text into comparable word tokens, ignoring layout, punctuation, case and ligatures. */
final class TextNorm {

	private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

	private TextNorm() {
	}

	static List<String> tokens(String text) {
		String folded = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		List<String> out = new ArrayList<>();
		for (String part : NOT_WORD.split(folded)) {
			if (!part.isEmpty()) {
				out.add(part);
			}
		}
		return out;
	}

	/**
	 * Letters and digits only, in one run. Comparing squashed text ignores spacing, so a word the typesetter
	 * split ("experi ence"), letter-spaced ("E X P E R I E N C E") or hyphenated across a line still matches.
	 */
	static String squash(String text) {
		return String.join("", tokens(text));
	}
}
