package com.jobsearch.pdfworker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Compiles a Typst source in a throwaway directory (see {@link TypstRunner} for how one {@code typst compile} is
 * run and confined). In fit mode the document is compiled several times with different values of the {@code scale}
 * input (read in the template through {@code sys.inputs}) to find the largest scale that still gives one page.
 */
final class Compiler {


	static final double MIN_SCALE = 0.88;
	static final double MAX_SCALE = 1.12;
	/** A one-page result at least this full counts as fitted. */
	static final double TARGET_FILL = 0.93;
	private static final double SCALE_PRECISION = 0.005;
	private static final int MAX_FIT_COMPILES = 7;

	private final TypstRunner runner;
	private final Duration timeout;

	Compiler(String typstBin, Duration timeout, int maxPdfBytes) {
		this.runner = new TypstRunner(typstBin, maxPdfBytes);
		this.timeout = timeout;
	}

	/** Single compile, no scale input. */
	CompileResult compile(String source) throws IOException, InterruptedException {
		return compile(source, false);
	}

	/**
	 * With {@code fit} the compile time of all attempts together stays within the configured timeout; when it runs
	 * out after at least one successful compile, the best result so far is returned.
	 */
	CompileResult compile(String source, boolean fit) throws IOException, InterruptedException {
		Path workDir = Files.createTempDirectory("pdf-worker-");
		try {
			Files.writeString(workDir.resolve("main.typ"), source, StandardCharsets.UTF_8);
			long deadline = System.nanoTime() + timeout.toNanos();
			return fit ? fit(workDir, deadline) : runner.run(workDir, null, deadline);
		}
		finally {
			deleteRecursively(workDir);
		}
	}

	private CompileResult fit(Path workDir, long deadline) throws IOException, InterruptedException {
		CompileResult first = runner.run(workDir, 1.0, deadline);
		if (!(first instanceof CompileResult.Ok best) || best.metrics() == null || isFitted(best.metrics())) {
			return first;
		}
		boolean overflow = best.metrics().pages() > 1;
		// The answer lies between lo (one page) and hi (wrong result); the first compile at 1.0 is one of them.
		double lo = overflow ? MIN_SCALE : 1.0;
		double hi = overflow ? 1.0 : MAX_SCALE;

		// The far bound first: if even that does not give the wanted result there is nothing to search.
		CompileResult boundResult = runner.run(workDir, overflow ? MIN_SCALE : MAX_SCALE, deadline);
		if (!(boundResult instanceof CompileResult.Ok bound) || bound.metrics() == null) {
			return fallback(best, boundResult);
		}
		boolean boundIsOnePage = bound.metrics().pages() == 1;
		if (boundIsOnePage != overflow) {
			// Two pages at MIN, or one page at MAX (its fill may still be low): the bound is the best there is.
			return bound;
		}
		if (overflow) {
			best = bound;
		}
		int compiles = 2;
		while (hi - lo >= SCALE_PRECISION && compiles < MAX_FIT_COMPILES) {
			double mid = round3((lo + hi) / 2);
			CompileResult attempt = runner.run(workDir, mid, deadline);
			compiles++;
			if (!(attempt instanceof CompileResult.Ok ok) || ok.metrics() == null) {
				return fallback(best, attempt);
			}
			if (ok.metrics().pages() == 1) {
				lo = mid;
				best = ok;
			}
			else {
				hi = mid;
			}
		}
		return best;
	}

	private static boolean isFitted(PageMetrics metrics) {
		return metrics.pages() == 1 && metrics.fill() >= TARGET_FILL;
	}

	/** A broken document is reported as such; running out of time (or an unmeasurable PDF) keeps the best so far. */
	private static CompileResult fallback(CompileResult.Ok best, CompileResult attempt) {
		return attempt instanceof CompileResult.Failed ? attempt : best;
	}

	private static double round3(double value) {
		return Math.round(value * 1000) / 1000.0;
	}

	private static void deleteRecursively(Path dir) throws IOException {
		List<Path> paths;
		try (Stream<Path> walk = Files.walk(dir)) {
			paths = walk.sorted(Comparator.reverseOrder()).toList();
		}
		for (Path path : paths) {
			Files.deleteIfExists(path);
		}
	}
}
