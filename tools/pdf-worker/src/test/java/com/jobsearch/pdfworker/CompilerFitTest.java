package com.jobsearch.pdfworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Fit mode against {@link TestSupport#scalableDocument}: about 58 lines fill the page at scale 1.0, and the page
 * holds roughly 47 lines at scale 1.12 and 70 at 0.88.
 */
class CompilerFitTest {

	@TempDir
	Path dir;

	private Path invocations;
	private Compiler compiler;

	@BeforeEach
	void setUp() throws Exception {
		TestSupport.assumeTypstInstalled();
		invocations = dir.resolve("invocations");
		// Counts how many times the real typst is started.
		Path wrapper = dir.resolve("typst-counting");
		Files.writeString(wrapper, "#!/bin/sh\necho x >> '" + invocations + "'\nexec typst \"$@\"\n");
		Files.setPosixFilePermissions(wrapper, PosixFilePermissions.fromString("rwxr-xr-x"));
		compiler = new Compiler(wrapper.toString(), Duration.ofSeconds(60), 10 * 1024 * 1024);
	}

	@Test
	void nonFitModeCompilesOnceWithoutScale() throws Exception {
		CompileResult.Ok ok = fit(50, false);

		assertNull(ok.scale());
		assertEquals(1, compileCount());
	}

	@Test
	void textThatAlreadyFitsIsKeptAtScaleOneAfterOneCompile() throws Exception {
		CompileResult.Ok ok = fit(56, true);

		assertEquals(1.0, ok.scale(), 1e-9);
		assertEquals(1, ok.metrics().pages());
		assertTrue(ok.metrics().fill() >= 0.93, "fill=" + ok.metrics().fill());
		assertEquals(1, compileCount());
	}

	@Test
	void shortTextScalesUpAndFillsThePage() throws Exception {
		CompileResult.Ok ok = fit(50, true);

		assertTrue(ok.scale() > 1.0 && ok.scale() <= Compiler.MAX_SCALE, "scale=" + ok.scale());
		assertEquals(1, ok.metrics().pages());
		assertTrue(ok.metrics().fill() >= 0.93, "fill=" + ok.metrics().fill());
		assertTrue(compileCount() <= 7, "compiles=" + compileCount());
	}

	@Test
	void textTooShortEvenForTheMaximumScaleReturnsTheMaximum() throws Exception {
		CompileResult.Ok ok = fit(20, true);

		assertEquals(Compiler.MAX_SCALE, ok.scale(), 1e-9);
		assertEquals(1, ok.metrics().pages());
		assertTrue(ok.metrics().fill() < 0.93, "fill=" + ok.metrics().fill());
		assertEquals(2, compileCount());
	}

	@Test
	void longTextScalesDownAndStillGivesOnePage() throws Exception {
		CompileResult.Ok ok = fit(63, true);

		assertTrue(ok.scale() < 1.0 && ok.scale() >= Compiler.MIN_SCALE, "scale=" + ok.scale());
		assertEquals(1, ok.metrics().pages());
		assertTrue(ok.metrics().fill() >= 0.93, "fill=" + ok.metrics().fill());
		assertTrue(compileCount() <= 7, "compiles=" + compileCount());
	}

	@Test
	void hopelessTextReturnsTwoPagesAtTheMinimumScale() throws Exception {
		CompileResult.Ok ok = fit(90, true);

		assertEquals(Compiler.MIN_SCALE, ok.scale(), 1e-9);
		assertEquals(2, ok.metrics().pages());
		assertEquals(2, compileCount());
	}

	@Test
	void brokenDocumentIsACompileErrorInFitMode() throws Exception {
		assertInstanceOf(CompileResult.Failed.class, compiler.compile(TestSupport.BROKEN_DOCUMENT, true));
	}

	@Test
	void wholeSearchSharesOneTimeBudgetAndReturnsTheBestSoFar() throws Exception {
		// The first two compiles (1.0, then the minimum scale) run normally; the third hangs and is cut off by the
		// shared deadline, so the result is the one-page compile at the minimum scale. No timing margin matters
		// as long as two normal compiles fit into the budget.
		Path hanging = dir.resolve("typst-hanging");
		Files.writeString(hanging, "#!/bin/sh\necho x >> '" + invocations + "'\n"
				+ "[ \"$(wc -l < '" + invocations + "')\" -ge 3 ] && sleep 60\nexec typst \"$@\"\n");
		Files.setPosixFilePermissions(hanging, PosixFilePermissions.fromString("rwxr-xr-x"));
		Compiler tight = new Compiler(hanging.toString(), Duration.ofSeconds(5), 10 * 1024 * 1024);
		long started = System.nanoTime();

		CompileResult result = tight.compile(TestSupport.scalableDocument(63), true);

		long millis = (System.nanoTime() - started) / 1_000_000;
		CompileResult.Ok ok = assertInstanceOf(CompileResult.Ok.class, result);
		assertEquals(Compiler.MIN_SCALE, ok.scale(), 1e-9);
		assertEquals(1, ok.metrics().pages());
		assertEquals(3, compileCount());
		assertTrue(millis < 20_000, "millis=" + millis);
	}

	private CompileResult.Ok fit(int lines, boolean fit) throws Exception {
		return assertInstanceOf(CompileResult.Ok.class, compiler.compile(TestSupport.scalableDocument(lines), fit));
	}

	private long compileCount() throws Exception {
		return Files.readAllLines(invocations).size();
	}
}
