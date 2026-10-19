package com.jobsearch.pdfworker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Runs {@code typst compile} on a Typst source in a throwaway directory.
 *
 * <p>The directory holds only {@code main.typ} and is the Typst project root, so the document cannot read other
 * files; {@code --ignore-system-fonts} limits fonts to the ones embedded in the binary. Packages are not provided:
 * an {@code @preview} import would be downloaded, which the network policy of the deployment forbids and a dead
 * proxy in the child's environment blocks as well. The child gets a cleared environment and file size and memory
 * limits, so one document cannot exhaust the host. In fit mode the
 * document is compiled several times with different values of the {@code scale} input (read in the template through
 * {@code sys.inputs}) to find the largest scale that still gives one page.
 */
final class Compiler {

	private static final Logger log = Logger.getLogger(Compiler.class.getName());
	private static final int MAX_LOG_CHARS = 4000;

	static final double MIN_SCALE = 0.88;
	static final double MAX_SCALE = 1.12;
	/** A one-page result at least this full counts as fitted. */
	static final double TARGET_FILL = 0.93;
	private static final double SCALE_PRECISION = 0.005;
	private static final int MAX_FIT_COMPILES = 7;
	/** Address space of one typst process (Linux), in KiB. */
	private static final long MEMORY_LIMIT_KB = 512 * 1024;
	private static final String DEAD_PROXY = "http://127.0.0.1:9";
	private static final int EXIT_SIGABRT = 128 + 6;
	private static final int EXIT_SIGXFSZ = 128 + 25;
	private static final int EXIT_NOT_EXECUTABLE = 126;
	private static final int EXIT_NOT_FOUND = 127;

	private final String typstBin;
	private final Duration timeout;
	private final int maxPdfBytes;

	Compiler(String typstBin, Duration timeout, int maxPdfBytes) {
		this.typstBin = typstBin;
		this.timeout = timeout;
		this.maxPdfBytes = maxPdfBytes;
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
			return fit ? fit(workDir, deadline) : run(workDir, null, deadline);
		}
		finally {
			deleteRecursively(workDir);
		}
	}

	private CompileResult fit(Path workDir, long deadline) throws IOException, InterruptedException {
		CompileResult first = run(workDir, 1.0, deadline);
		if (!(first instanceof CompileResult.Ok best) || best.metrics() == null || isFitted(best.metrics())) {
			return first;
		}
		boolean overflow = best.metrics().pages() > 1;
		// The answer lies between lo (one page) and hi (wrong result); the first compile at 1.0 is one of them.
		double lo = overflow ? MIN_SCALE : 1.0;
		double hi = overflow ? 1.0 : MAX_SCALE;

		// The far bound first: if even that does not give the wanted result there is nothing to search.
		CompileResult boundResult = run(workDir, overflow ? MIN_SCALE : MAX_SCALE, deadline);
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
			CompileResult attempt = run(workDir, mid, deadline);
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

	/** One {@code typst compile}; {@code scale} null means no scale input. */
	private CompileResult run(Path workDir, Double scale, long deadline) throws IOException, InterruptedException {
		long remainingNanos = deadline - System.nanoTime();
		if (remainingNanos <= 0) {
			return new CompileResult.TimedOut();
		}
		Path pdf = workDir.resolve("main.pdf");
		Files.deleteIfExists(pdf);
		// Output goes to a file, not a pipe: reading a pipe blocks until the process exits,
		// which would make the timeout below unreachable.
		Path logFile = workDir.resolve("compile.log");
		// The shell only sets resource limits and then becomes typst (exec), so the pid, the exit status and the
		// kill logic below see typst itself. A document must not be able to write an unbounded PDF or eat all memory.
		List<String> command = new ArrayList<>(List.of("sh", "-c", limits() + "exec \"$0\" \"$@\"",
				typstBin, "compile", "--root", workDir.toString(), "--ignore-system-fonts"));
		if (scale != null) {
			command.add("--input");
			command.add(String.format(Locale.ROOT, "scale=%.3f", scale));
		}
		command.add("main.typ");
		command.add("main.pdf");
		ProcessBuilder builder = new ProcessBuilder(command)
				.directory(workDir.toFile())
				.redirectErrorStream(true)
				.redirectOutput(logFile.toFile());
		// Nothing from the worker's environment (the token!) reaches the document's compiler.
		Map<String, String> env = builder.environment();
		env.clear();
		env.put("PATH", System.getenv().getOrDefault("PATH", "/usr/local/bin:/usr/bin:/bin"));
		// Typst wants no writable location with --root, but if it ever asks for a cache or home directory, give it
		// one inside the throwaway directory (the root filesystem is read-only in production).
		env.put("HOME", workDir.toString());
		env.put("XDG_CACHE_HOME", workDir.toString());
		// `#import "@preview/..."` would download and run third-party code where there is network egress. Production
		// blocks egress by network policy; the dead proxy makes the download fail everywhere else too.
		for (String name : List.of("HTTPS_PROXY", "HTTP_PROXY", "ALL_PROXY", "https_proxy", "http_proxy", "all_proxy")) {
			env.put(name, DEAD_PROXY);
		}
		Process process = builder.start();

		boolean finished;
		try {
			finished = process.waitFor(remainingNanos, TimeUnit.NANOSECONDS);
		}
		catch (InterruptedException ex) {
			kill(process);
			throw ex;
		}
		if (!finished) {
			kill(process);
			return new CompileResult.TimedOut();
		}

		int exit = process.exitValue();
		if (exit == EXIT_SIGXFSZ) {
			// The file size limit stopped typst while writing the PDF.
			return new CompileResult.Failed("PDF is larger than " + maxPdfBytes + " bytes");
		}
		if (exit == EXIT_SIGABRT && memoryLimited()) {
			// Rust aborts when an allocation fails, which under the address space limit means the document is too big.
			return new CompileResult.Failed("Typst ran out of memory: the document is too large or too complex");
		}
		if (exit > 128) {
			// Killed by some other signal (the OOM killer, a crash): the document is not necessarily at fault.
			throw new IOException("typst was killed by signal " + (exit - 128));
		}
		if (exit == EXIT_NOT_EXECUTABLE || exit == EXIT_NOT_FOUND) {
			// The shell could not start typst (wrong TYPST_BIN, broken image): a worker fault, not the document's.
			throw new IOException("typst could not be started");
		}
		if (exit != 0 || !Files.exists(pdf)) {
			return new CompileResult.Failed(tail(Files.readString(logFile, StandardCharsets.UTF_8)));
		}
		if (Files.size(pdf) > maxPdfBytes) {
			return new CompileResult.Failed("PDF is larger than " + maxPdfBytes + " bytes");
		}
		byte[] bytes = Files.readAllBytes(pdf);
		return new CompileResult.Ok(bytes, measure(bytes), scale);
	}

	/** {@code ulimit} commands for the shell that starts typst. */
	private String limits() {
		// `ulimit -f` counts 512-byte blocks in POSIX sh; a little slack so that the cap check below, not the
		// signal, reports a PDF that is only slightly too large.
		long blocks = (maxPdfBytes + 511) / 512 + 64;
		String limits = "ulimit -f " + blocks + "; ";
		if (memoryLimited()) {
			limits += "ulimit -v " + MEMORY_LIMIT_KB + "; ";
		}
		return limits;
	}

	/** The address space limit is not enforced on macOS, so it is applied on Linux only. */
	private static boolean memoryLimited() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
	}

	private static double round3(double value) {
		return Math.round(value * 1000) / 1000.0;
	}

	private static PageMetrics measure(byte[] pdf) {
		try {
			return PageMetrics.measure(pdf);
		}
		catch (IOException | RuntimeException | StackOverflowError ex) {
			log.log(Level.WARNING, "page metrics failed", ex);
			return null;
		}
	}

	private static void kill(Process process) throws InterruptedException {
		process.descendants().forEach(ProcessHandle::destroyForcibly);
		process.destroyForcibly().waitFor();
	}

	private static String tail(String log) {
		String trimmed = log.strip();
		if (trimmed.isEmpty()) {
			return "Typst compilation failed with no output";
		}
		return trimmed.length() > MAX_LOG_CHARS ? trimmed.substring(trimmed.length() - MAX_LOG_CHARS) : trimmed;
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
