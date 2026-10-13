package com.jobsearch.atsworker;

import java.util.List;

/**
 * What an ATS would make of a resume PDF. {@code verdict} is {@code ok}, {@code warnings} or {@code issues}
 * (at least one error). {@code coverage} and {@code orderScore} are null when no expected text was given.
 */
record Report(
		String verdict,
		int score,
		int pages,
		int textChars,
		Double coverage,
		Double orderScore,
		List<String> sectionsFound,
		List<String> sectionsMissing,
		Contacts contacts,
		List<String> lostLines,
		List<Issue> issues,
		String text,
		boolean textTruncated
) {

	record Issue(String code, Severity severity, String message) {

		static Issue error(String code, String message) {
			return new Issue(code, Severity.ERROR, message);
		}

		static Issue warning(String code, String message) {
			return new Issue(code, Severity.WARNING, message);
		}

		static Issue info(String code, String message) {
			return new Issue(code, Severity.INFO, message);
		}
	}

	/** Whether each kind of contact detail is readable in the PDF text. */
	record Contacts(boolean email, boolean phone, boolean url, boolean nameAtTop) {
	}
}
