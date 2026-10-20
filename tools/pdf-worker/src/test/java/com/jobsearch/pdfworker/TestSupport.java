package com.jobsearch.pdfworker;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class TestSupport {

	static final String BROKEN_DOCUMENT = "#undefined-function(1)";
	/** About 1e10 cheap loop iterations: runs for minutes without allocating memory. */
	static final String ENDLESS_DOCUMENT = "#for i in range(100000) { for j in range(100000) { let x = i * j } }\nhi";

	private TestSupport() {
	}

	/** Skips the calling test on machines without typst instead of failing it. */
	static void assumeTypstInstalled() {
		boolean installed;
		try {
			installed = new ProcessBuilder("typst", "--version").start().waitFor() == 0;
		}
		catch (IOException | InterruptedException ex) {
			installed = false;
		}
		assumeTrue(installed, "typst is not installed");
	}

	static String warmupSource() throws IOException {
		try (InputStream in = TestSupport.class.getResourceAsStream("/warmup.typ")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/**
	 * A stand-in for the resume template: A4, equal margins, {@code lines} short lines whose size follows the
	 * {@code scale} input, so one page holds about 60 lines at scale 1.0 and about 47 at 1.12.
	 */
	static String scalableDocument(int lines) {
		return """
				#let scale = float(sys.inputs.at("scale", default: "1.0"))
				#set page(paper: "a4", margin: 1.5cm, numbering: none)
				#set text(font: "Libertinus Serif", size: 10pt * scale)
				#set par(leading: 0.65em * scale, spacing: 0.65em * scale)
				#for i in range(%d) [Line #i, with a few words to look like text.

				]
				""".formatted(lines);
	}
}
