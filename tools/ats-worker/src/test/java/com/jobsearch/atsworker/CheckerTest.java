package com.jobsearch.atsworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CheckerTest {

	private final Checker checker = new Checker(10);

	private Report check(byte[] pdf, String expected) throws IOException {
		return checker.check(pdf, expected, Deadline.after(Duration.ofSeconds(20)));
	}

	private static boolean has(Report report, String code) {
		return report.issues().stream().anyMatch(issue -> issue.code().equals(code));
	}

	@Test
	void aCleanSingleColumnResumeIsOk() throws IOException {
		Report report = check(TestPdf.singleColumn(TestPdf.RESUME), TestPdf.RESUME);

		assertEquals("ok", report.verdict(), report.issues().toString());
		assertTrue(report.coverage() > 0.99);
		assertTrue(report.orderScore() > 0.99);
		assertTrue(report.sectionsFound().containsAll(java.util.List.of("experience", "education", "skills")));
		assertTrue(report.contacts().email() && report.contacts().phone() && report.contacts().nameAtTop());
		assertTrue(report.lostLines().isEmpty());
		assertTrue(report.score() >= 95);
		assertTrue(report.text().contains("payment gateway"));
	}

	@Test
	void textMissingFromThePdfIsReportedAsLost() throws IOException {
		String shortened = TestPdf.RESUME.lines()
				.filter(line -> !line.startsWith("- ") && !line.startsWith("Languages") && !line.startsWith("Tools"))
				.reduce((a, b) -> a + "\n" + b).orElseThrow();

		Report report = check(TestPdf.singleColumn(shortened), TestPdf.RESUME);

		assertEquals("issues", report.verdict());
		assertTrue(has(report, "text_lost"));
		assertTrue(report.coverage() < 0.85);
		assertTrue(report.lostLines().stream().anyMatch(line -> line.contains("payment gateway")));
	}

	@Test
	void aHeadingNobodyRecognisesIsAnError() throws IOException {
		String renamed = TestPdf.RESUME.replace("Experience\nSenior", "My Journey\nSenior");

		Report report = check(TestPdf.singleColumn(renamed), TestPdf.RESUME);

		assertEquals("issues", report.verdict());
		assertTrue(has(report, "section_not_recognised"));
		assertEquals(java.util.List.of("experience"), report.sectionsMissing());
	}

	@Test
	void aLetterSpacedHeadingStillCounts() throws IOException {
		String spaced = TestPdf.RESUME.replace("\nExperience\n", "\nE X P E R I E N C E\n");

		Report report = check(TestPdf.singleColumn(spaced), TestPdf.RESUME);

		assertFalse(has(report, "section_not_recognised"), report.issues().toString());
	}

	@Test
	void anEmailThatCannotBeReadIsAnError() throws IOException {
		String withoutEmail = TestPdf.RESUME.replace(" | jane.doe@example.com", "");

		Report report = check(TestPdf.singleColumn(withoutEmail), TestPdf.RESUME);

		assertTrue(has(report, "email_not_found"));
		assertFalse(report.contacts().email());
		assertEquals("issues", report.verdict());
	}

	@Test
	void twoColumnsAreFlagged() throws IOException {
		String text = (TestPdf.RESUME + "\n").repeat(3);

		Report report = check(TestPdf.twoColumns(text), null);

		assertTrue(has(report, "columns"), report.issues().toString());
	}

	@Test
	void aSingleColumnIsNotFlaggedAsColumns() throws IOException {
		Report report = check(TestPdf.singleColumn((TestPdf.RESUME + "\n").repeat(3)), null);

		assertFalse(has(report, "columns"));
	}

	@Test
	void withoutExpectedTextOnlyTheReferenceFreeChecksRun() throws IOException {
		Report report = check(TestPdf.singleColumn(TestPdf.RESUME), null);

		assertNull(report.coverage());
		assertNull(report.orderScore());
		assertEquals("ok", report.verdict(), report.issues().toString());
	}

	@Test
	void withoutExpectedTextMissingCoreHeadingsAreWarnings() throws IOException {
		Report report = check(TestPdf.singleColumn("Jane Doe\nI write software for a living and enjoy it a lot, "
				+ "mostly backend services in Java."), null);

		assertEquals("warnings", report.verdict());
		assertTrue(has(report, "section_missing"));
	}

	@Test
	void aPdfWithoutATextLayerIsAnErrorWithScoreZero() throws IOException {
		Report report = check(TestPdf.withoutText(), TestPdf.RESUME);

		assertEquals("issues", report.verdict());
		assertTrue(has(report, "no_text_layer"));
		assertEquals(0, report.score());
		assertNotNull(report.contacts());
	}

	@Test
	void tooManyPagesAreRefused() throws IOException {
		CheckFailure failure = assertThrows(CheckFailure.class,
				() -> new Checker(2).check(TestPdf.pages(3), null, Deadline.after(Duration.ofSeconds(20))));

		assertEquals("too_many_pages", failure.code());
	}

	@Test
	void anExpiredDeadlineStopsTheCheck() {
		CheckFailure failure = assertThrows(CheckFailure.class,
				() -> checker.check(TestPdf.singleColumn(TestPdf.RESUME), null, Deadline.after(Duration.ofSeconds(-1))));

		assertEquals("check_timeout", failure.code());
	}
}
