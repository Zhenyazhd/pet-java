package com.jobsearch.latexworker;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class TestSupport {

	static final String BROKEN_DOCUMENT = "\\documentclass{article}\\begin{document}\\undefinedmacro\\end{document}";
	static final String ENDLESS_DOCUMENT = "\\documentclass{article}\\begin{document}\\def\\x{\\x}\\x\\end{document}";

	private TestSupport() {
	}

	/** Skips the calling test on machines without tectonic instead of failing it. */
	static void assumeTectonicInstalled() {
		boolean installed;
		try {
			installed = new ProcessBuilder("tectonic", "--version").start().waitFor() == 0;
		}
		catch (IOException | InterruptedException ex) {
			installed = false;
		}
		assumeTrue(installed, "tectonic is not installed");
	}

	static String warmupSource() throws IOException {
		try (InputStream in = TestSupport.class.getResourceAsStream("/warmup.tex")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
