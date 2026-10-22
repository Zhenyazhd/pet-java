package com.jobsearch.atsworker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Decides whether an ATS could read a resume PDF; see README.md for the checks and the score weights. */
final class Checker {

	static final int MAX_TEXT_IN_REPORT = 20_000;

	private static final int MIN_TEXT_CHARS = 50;
	private static final double COVERAGE_ERROR = 0.85;
	private static final double COVERAGE_WARNING = 0.95;
	private static final double ORDER_ERROR = 0.7;
	private static final double ORDER_WARNING = 0.9;
	private static final double LOST_LINE_COVERAGE = 0.6;
	private static final int MIN_TOKENS_FOR_ORDER = 20;
	private static final int MAX_TOKENS_FOR_ORDER = 6000;
	private static final int MIN_TOKEN_LENGTH_FOR_SQUASH_MATCH = 4;
	private static final int MAX_LOST_LINES = 10;
	private static final int NAME_LOOKAHEAD_LINES = 8;
	private static final double GARBLED_RATIO = 0.01;

	private final int maxPages;

	Checker(int maxPages) {
		this.maxPages = maxPages;
	}

	/** @param expected the text the PDF was built from; without it only the checks that need no reference run */
	Report check(byte[] pdf, String expected, Deadline deadline) throws IOException {
		PdfText.Extracted extracted = PdfText.read(pdf, maxPages, deadline);
		String text = extracted.stream();
		boolean hasExpected = expected != null && !expected.isBlank();

		Findings findings = new Findings();
		if (text.strip().length() < MIN_TEXT_CHARS) {
			findings.add(Report.Issue.error("no_text_layer",
					"The PDF has no readable text (an image or an empty file). An ATS reads text, not pictures."), 100);
			return report(extracted, findings, null, null, List.of(), List.of(), List.of(),
					new Report.Contacts(false, false, false, false));
		}

		garbled(text, findings);
		deadline.check();
		Double coverage = null;
		List<String> lostLines = List.of();
		Double order = null;
		if (hasExpected) {
			Coverage result = coverage(expected, text);
			coverage = result.ratio();
			lostLines = result.lostLines();
			coverageIssue(coverage, lostLines, findings);
			deadline.check();
			order = Math.min(order(expected, text), order(expected, extracted.visual()));
			orderIssue(order, findings);
		}
		deadline.check();

		List<String> found = Sections.find(text);
		List<String> missing = missingSections(hasExpected ? expected : null, found, findings);
		Report.Contacts contacts = contacts(hasExpected ? expected : null, extracted, findings);

		if (extracted.columns()) {
			findings.add(Report.Issue.warning("columns", "The text is laid out in columns. Parsers often read "
					+ "columns across, mixing the left and right text. Use a single column."), 25);
		}
		if (extracted.largeImages() > 0) {
			findings.add(Report.Issue.info("images", "The PDF has " + extracted.largeImages()
					+ " image(s). An ATS ignores images, so nothing important may be inside them."), 0);
		}
		if (extracted.pages() > 2) {
			findings.add(Report.Issue.info("long", "The resume is " + extracted.pages()
					+ " pages long; two is the usual maximum."), 0);
		}
		return report(extracted, findings, coverage, order, found, missing, lostLines, contacts);
	}

	private static Report report(PdfText.Extracted extracted, Findings findings, Double coverage, Double order,
			List<String> found, List<String> missing, List<String> lostLines, Report.Contacts contacts) {
		int score = Math.max(0, 100 - findings.penalty);
		if (coverage != null) {
			score = Math.max(0, score - (int) Math.round((1 - coverage) * 60));
		}
		if (order != null) {
			score = Math.max(0, score - (int) Math.round((1 - order) * 20));
		}
		String verdict = findings.has(Severity.ERROR) ? "issues" : findings.has(Severity.WARNING) ? "warnings" : "ok";
		String text = extracted.stream().strip();
		boolean truncated = text.length() > MAX_TEXT_IN_REPORT;
		return new Report(verdict, score, extracted.pages(), extracted.stream().length(), coverage, order, found,
				missing, contacts, lostLines, findings.issues, truncated ? text.substring(0, MAX_TEXT_IN_REPORT) : text,
				truncated);
	}

	/** Collects issues and the score points each one costs. */
	private static final class Findings {

		final List<Report.Issue> issues = new ArrayList<>();
		int penalty;

		void add(Report.Issue issue, int points) {
			issues.add(issue);
			penalty += points;
		}

		boolean has(Severity severity) {
			return issues.stream().anyMatch(issue -> issue.severity() == severity);
		}
	}

	private static void garbled(String text, Findings findings) {
		long bad = text.chars().filter(c -> c == 0xFFFD || (Character.isISOControl(c) && !Character.isWhitespace(c))).count();
		if ((double) bad / text.length() > GARBLED_RATIO) {
			findings.add(Report.Issue.error("garbled_text", "Part of the text came out as unreadable symbols. "
					+ "The PDF fonts do not carry a text mapping, so an ATS reads garbage."), 40);
		}
	}

	private record Coverage(double ratio, List<String> lostLines) {
	}

	/**
	 * Share of the expected words found in the PDF text, counting each occurrence once. A word the PDF text
	 * lacks as a token still counts when it appears inside the squashed text, which covers words the typesetter
	 * split by spacing, kerning or a line-end hyphen.
	 */
	private static Coverage coverage(String expected, String extracted) {
		Map<String, Integer> pool = new HashMap<>();
		for (String token : TextNorm.tokens(extracted)) {
			pool.merge(token, 1, Integer::sum);
		}
		String squashed = TextNorm.squash(extracted);
		long total = 0;
		long hit = 0;
		List<String> lost = new ArrayList<>();
		for (String line : expected.lines().toList()) {
			List<String> tokens = TextNorm.tokens(line);
			if (tokens.isEmpty()) {
				continue;
			}
			int lineHit = 0;
			for (String token : tokens) {
				Integer left = pool.get(token);
				if (left != null && left > 0) {
					pool.put(token, left - 1);
					lineHit++;
				}
				else if (token.length() >= MIN_TOKEN_LENGTH_FOR_SQUASH_MATCH && squashed.contains(token)) {
					lineHit++;
				}
			}
			total += tokens.size();
			hit += lineHit;
			if (tokens.size() >= 3 && lineHit < LOST_LINE_COVERAGE * tokens.size() && lost.size() < MAX_LOST_LINES) {
				String stripped = line.strip();
				lost.add(stripped.length() > 120 ? stripped.substring(0, 120) + "…" : stripped);
			}
		}
		return new Coverage(total == 0 ? 1.0 : (double) hit / total, lost);
	}

	private static void coverageIssue(double coverage, List<String> lostLines, Findings findings) {
		String percent = Math.round(coverage * 100) + "%";
		if (coverage < COVERAGE_ERROR) {
			findings.add(Report.Issue.error("text_lost", "Only " + percent + " of the resume text can be read from "
					+ "the PDF. The rest is lost: see the lost lines."), 0);
		}
		else if (coverage < COVERAGE_WARNING) {
			findings.add(Report.Issue.warning("text_partly_lost", percent + " of the resume text can be read from "
					+ "the PDF; some lines are missing."), 0);
		}
	}

	/** How much of the reading sequence survives, 1.0 when the words come in the order they were written. */
	private static double order(String expected, String extracted) {
		List<String> want = TextNorm.tokens(expected);
		List<String> got = TextNorm.tokens(extracted);
		Set<String> shared = new HashSet<>(want);
		shared.retainAll(new HashSet<>(got));
		List<String> a = want.stream().filter(shared::contains).limit(MAX_TOKENS_FOR_ORDER).toList();
		List<String> b = got.stream().filter(shared::contains).limit(MAX_TOKENS_FOR_ORDER).toList();
		if (a.size() < MIN_TOKENS_FOR_ORDER) {
			return 1.0;
		}
		return (double) lcs(a, b) / a.size();
	}

	private static int lcs(List<String> a, List<String> b) {
		int[] previous = new int[b.size() + 1];
		int[] current = new int[b.size() + 1];
		for (String x : a) {
			for (int j = 1; j <= b.size(); j++) {
				current[j] = x.equals(b.get(j - 1)) ? previous[j - 1] + 1 : Math.max(previous[j], current[j - 1]);
			}
			int[] swap = previous;
			previous = current;
			current = swap;
		}
		return previous[b.size()];
	}

	private static void orderIssue(double order, Findings findings) {
		String percent = Math.round(order * 100) + "%";
		if (order < ORDER_ERROR) {
			findings.add(Report.Issue.error("reading_order", "The text comes out in a scrambled order (" + percent
					+ " in sequence). An ATS would mix up jobs, dates and skills."), 0);
		}
		else if (order < ORDER_WARNING) {
			findings.add(Report.Issue.warning("reading_order", "Parts of the text come out in a different order ("
					+ percent + " in sequence)."), 0);
		}
	}

	private static List<String> missingSections(String expected, List<String> found, Findings findings) {
		List<String> missing = new ArrayList<>();
		if (expected != null) {
			for (String section : Sections.find(expected)) {
				if (!found.contains(section)) {
					missing.add(section);
					findings.add(Report.Issue.error("section_not_recognised", "The '" + section + "' section is not "
							+ "recognised. Use a standard heading, such as 'Experience', 'Education' or 'Skills'."), 10);
				}
			}
		}
		else {
			for (String section : Sections.CORE) {
				if (!found.contains(section)) {
					missing.add(section);
					findings.add(Report.Issue.warning("section_missing", "No '" + section + "' heading found. An ATS "
							+ "looks for standard headings."), 5);
				}
			}
		}
		return missing;
	}

	private static Report.Contacts contacts(String expected, PdfText.Extracted extracted, Findings findings) {
		ContactInfo present = ContactInfo.of(extracted.stream());
		boolean nameAtTop = true;
		if (expected != null) {
			ContactInfo wanted = ContactInfo.of(expected);
			require(wanted.email(), present.email(), "email", "The email address", findings);
			require(wanted.phone(), present.phone(), "phone", "The phone number", findings);
			require(wanted.url(), present.url(), "url", "The profile link", findings);
			nameAtTop = nameAtTop(expected, extracted);
			if (!nameAtTop) {
				findings.add(Report.Issue.warning("name_not_at_top", "Your name is not among the first lines of the "
						+ "text. An ATS expects it at the top."), 5);
			}
		}
		return new Report.Contacts(present.email(), present.phone(), present.url(), nameAtTop);
	}

	private static void require(boolean wanted, boolean present, String code, String what, Findings findings) {
		if (wanted && !present) {
			findings.add(Report.Issue.error(code + "_not_found", what + " cannot be read from the PDF."), 10);
		}
	}

	/** The first line of the expected text is the name; it must show up near the top in either reading order. */
	private static boolean nameAtTop(String expected, PdfText.Extracted extracted) {
		String name = expected.lines().map(String::strip).filter(line -> !line.isEmpty()).findFirst().orElse("");
		String wanted = TextNorm.squash(name);
		if (wanted.isEmpty()) {
			return true;
		}
		return topLinesContain(extracted.stream(), wanted) || topLinesContain(extracted.visual(), wanted);
	}

	private static boolean topLinesContain(String text, String wanted) {
		String top = text.lines().map(String::strip).filter(line -> !line.isEmpty()).limit(NAME_LOOKAHEAD_LINES)
				.reduce("", (a, b) -> a + " " + b);
		return TextNorm.squash(top).contains(wanted);
	}
}
