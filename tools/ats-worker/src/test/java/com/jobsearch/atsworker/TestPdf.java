package com.jobsearch.atsworker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Builds small PDFs with a real text layer for the tests. */
final class TestPdf {

	static final String RESUME = """
			Jane Doe
			Backend Engineer
			+49 170 1234567 | jane.doe@example.com
			Profile
			Backend engineer with six years of experience building payment services in Java and PostgreSQL.
			Experience
			Senior Engineer — Acme Corp — 2020 – 2024
			- Built a payment gateway handling two million transactions a day
			- Reduced the p99 latency of the checkout API by forty percent
			Education
			BSc Computer Science — Technical University of Munich
			Skills
			Languages: Java, Kotlin, SQL
			Tools: Docker, Kubernetes, Terraform""";

	private record Placed(float x, float y, String text) {
	}

	private TestPdf() {
	}

	/** One column: every line of {@code text} below the previous one. */
	static byte[] singleColumn(String text) throws IOException {
		return build(1, lines(text, 50, 780));
	}

	/** Two columns: the first half of the lines on the left, the rest on the right, side by side. */
	static byte[] twoColumns(String text) throws IOException {
		List<String> all = text.lines().toList();
		int half = (all.size() + 1) / 2;
		List<Placed> placed = new java.util.ArrayList<>(lines(String.join("\n", all.subList(0, half)), 40, 780));
		placed.addAll(lines(String.join("\n", all.subList(half, all.size())), 320, 780));
		return build(1, placed);
	}

	static byte[] withoutText() throws IOException {
		return build(1, List.of());
	}

	static byte[] pages(int count) throws IOException {
		return build(count, lines("Jane Doe", 50, 780));
	}

	private static List<Placed> lines(String text, float x, float top) {
		List<Placed> out = new java.util.ArrayList<>();
		float y = top;
		for (String line : text.lines().toList()) {
			out.add(new Placed(x, y, line));
			y -= 18;
		}
		return out;
	}

	private static byte[] build(int pageCount, List<Placed> placed) throws IOException {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			for (int p = 0; p < pageCount; p++) {
				PDPage page = new PDPage(PDRectangle.A4);
				document.addPage(page);
				if (p > 0 || pageCount == 1 || !placed.isEmpty()) {
					try (PDPageContentStream content = new PDPageContentStream(document, page)) {
						content.setFont(font, 9);
						for (Placed item : placed) {
							content.beginText();
							content.newLineAtOffset(item.x(), item.y());
							content.showText(item.text().replace('—', '-').replace('–', '-'));
							content.endText();
						}
					}
				}
			}
			document.save(out);
			return out.toByteArray();
		}
	}
}
