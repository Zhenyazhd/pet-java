package com.jobsearch.atsworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The checks on PDFs typeset by tectonic with the production template (see src/test/resources/pdfs/README.md). */
class RealPdfTest {

	private final Checker checker = new Checker(10);

	private static byte[] pdf(String name) throws IOException {
		try (InputStream in = RealPdfTest.class.getResourceAsStream("/pdfs/" + name + ".pdf")) {
			return in.readAllBytes();
		}
	}

	private static String text(String name) throws IOException {
		try (InputStream in = RealPdfTest.class.getResourceAsStream("/pdfs/" + name + ".txt")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private Report check(String pdf, String expected) throws IOException {
		return checker.check(pdf(pdf), text(expected), Deadline.after(Duration.ofSeconds(20)));
	}

	private static boolean has(Report report, String code) {
		return report.issues().stream().anyMatch(issue -> issue.code().equals(code));
	}

	@Test
	void aHealthyResumeIsOkWhateverTheNumberOfRoles() throws IOException {
		Report report = check("six-en", "six-en");

		assertEquals("ok", report.verdict(), report.issues().toString());
		assertTrue(report.coverage() > 0.99, "coverage " + report.coverage());
		assertTrue(report.orderScore() > 0.95, "order " + report.orderScore());
		assertTrue(report.lostLines().isEmpty());
	}

	@Test
	void frenchHeadingsAreRecognisedAndTheTextMatches() throws IOException {
		Report report = check("six-fr", "six-fr");

		assertEquals("ok", report.verdict(), report.issues().toString());
		assertTrue(report.sectionsFound().containsAll(java.util.List.of("experience", "education", "skills")));
		assertTrue(report.coverage() > 0.99, "coverage " + report.coverage());
	}

	@Test
	void columnsWithLatexsDefaultGutterAreFlagged() throws IOException {
		assertTrue(has(check("multicol-en", "six-en"), "columns"));
	}

	@Test
	void aNarrowSidebarIsFlagged() throws IOException {
		assertTrue(has(check("sidebar-en", "six-en"), "columns"));
	}

	@Test
	void aSkillsTableInASingleColumnIsNotAColumnLayout() throws IOException {
		Report report = check("skills-table-en", "six-en");

		assertFalse(has(report, "columns"), report.issues().toString());
		assertEquals("ok", report.verdict(), report.issues().toString());
	}
}
