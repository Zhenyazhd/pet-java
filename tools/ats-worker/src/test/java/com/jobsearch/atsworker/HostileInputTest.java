package com.jobsearch.atsworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;

/** Inputs an attacker (or a bug) could send; each used to cost a CPU, the heap or a slot. */
class HostileInputTest {

	private final Checker checker = new Checker(10);

	@Test
	void aVeryLongUnbrokenWordDoesNotMakeTheContactScanQuadratic() {
		String word = "a".repeat(100_000);

		ContactInfo info = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> ContactInfo.of(word + " " + word));

		assertFalse(info.email());
		assertFalse(info.phone());
	}

	@Test
	void longRunsOfDigitsAndSpacesAreLinearToo() {
		String digits = "1 ".repeat(50_000) + "x";

		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> ContactInfo.of(digits));
	}

	@Test
	void anEmailAndAPhoneAreStillFound() {
		ContactInfo info = ContactInfo.of("Jane  +33 6 12 34 56 78 | jane.doe+cv@mail.example.com | linkedin.com/in/jane");

		assertTrue(info.email());
		assertTrue(info.phone());
		assertTrue(info.url());
	}

	@Test
	void twoDateRangesOnSeparateLinesAreNotAPhoneNumber() {
		assertFalse(ContactInfo.of("2013 – 2017\n2017 – 2020\n2020 – 2024").phone());
	}

	@Test
	void aPageOfAbsurdSizeIsRefusedBeforeAnythingIsAllocatedForIt() throws IOException {
		byte[] pdf = onePage(new PDRectangle(20_000_000f, 800f));

		CheckFailure failure = assertThrows(CheckFailure.class,
				() -> checker.check(pdf, null, Deadline.after(Duration.ofSeconds(20))));

		assertEquals("page_too_large", failure.code());
	}

	@Test
	void aPageAsWideAsASheetOfPaperIsFine() throws IOException {
		Report report = assertTimeoutPreemptively(Duration.ofSeconds(10),
				() -> checker.check(onePage(PDRectangle.A4), null, Deadline.after(Duration.ofSeconds(20))));

		assertEquals(1, report.pages());
	}

	@Test
	void anEncryptedPdfIsRefusedWithAClearCode() throws IOException {
		byte[] pdf;
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			document.addPage(new PDPage());
			StandardProtectionPolicy policy = new StandardProtectionPolicy("owner", "user-password", new AccessPermission());
			policy.setEncryptionKeyLength(128);
			document.protect(policy);
			document.save(out);
			pdf = out.toByteArray();
		}

		CheckFailure failure = assertThrows(CheckFailure.class,
				() -> checker.check(pdf, null, Deadline.after(Duration.ofSeconds(20))));

		assertEquals("encrypted", failure.code());
	}

	@Test
	void tooMuchTextIsRefused() throws IOException {
		byte[] pdf;
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDPage page = new PDPage(PDRectangle.A4);
			document.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 1);
				String line = "x".repeat(400);
				for (int i = 0; i < 800; i++) {
					content.beginText();
					content.newLineAtOffset(10, 5 + (i % 800) * 0.5f);
					content.showText(line);
					content.endText();
				}
			}
			document.save(out);
			pdf = out.toByteArray();
		}

		CheckFailure failure = assertThrows(CheckFailure.class,
				() -> checker.check(pdf, null, Deadline.after(Duration.ofSeconds(20))));

		assertEquals("too_much_text", failure.code());
	}

	private static byte[] onePage(PDRectangle size) throws IOException {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDPage page = new PDPage(size);
			document.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
				content.beginText();
				content.newLineAtOffset(20, 40);
				content.showText("Jane Doe, backend engineer with a short text layer that is long enough to count.");
				content.endText();
			}
			document.save(out);
			return out.toByteArray();
		}
	}
}
