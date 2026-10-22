package com.jobsearch.joblist.job;

import com.jobsearch.joblist.common.Hashes;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Identity of a vacancy across boards: the same company, title and place give the same value, whatever the
 * casing, punctuation or gender marker ("(m/f/d)", "H/F") the board adds.
 */
public final class JobFingerprint {

	private static final Pattern GENDER_MARKER = Pattern.compile("\\(?\\b(?:[hfmwd]\\s*/\\s*){1,2}[hfmwdx]\\b\\)?", Pattern.CASE_INSENSITIVE);
	private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");

	private JobFingerprint() {
	}

	public static String of(Job job) {
		String country = job.getCountries().isEmpty() ? "" : job.getCountries().getFirst();
		String city = job.getCities().isEmpty() ? "" : job.getCities().getFirst();
		return Hashes.sha256(normalise(job.getCompany()), normalise(GENDER_MARKER.matcher(job.getTitle()).replaceAll(" ")),
				country, normalise(city));
	}

	private static String normalise(String text) {
		String folded = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		return NOT_ALPHANUMERIC.matcher(folded).replaceAll(" ").strip();
	}
}
