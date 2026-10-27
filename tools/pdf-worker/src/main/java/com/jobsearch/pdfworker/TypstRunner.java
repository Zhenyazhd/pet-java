package com.jobsearch.pdfworker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One {@code typst compile} of the {@code main.typ} in a directory.
 *
 * <p>The directory is the Typst project root, so the document cannot read other files; {@code --ignore-system-fonts}
 * limits fonts to the ones embedded in the binary plus the directory in {@code TYPST_FONT_PATH} (the image's fonts). Packages are not provided: an {@code @preview} import would be
 * downloaded, which the network policy of the deployment forbids and a dead proxy in the child's environment blocks
 * as well. The child gets a cleared environment and file size and memory limits, so one document cannot exhaust the host.
 */
final class TypstRunner {

	private static final Logger log = Logger.getLogger(TypstRunner.class.getName());
	private static final int MAX_LOG_CHARS = 4000;
	/** Address space of one typst process (Linux), in KiB. */
	private static final long MEMORY_LIMIT_KB = 512 * 1024;
	private static final String DEAD_PROXY = "http://127.0.0.1:9";
	private static final int EXIT_SIGABRT = 128 + 6;
	private static final int EXIT_SIGXFSZ = 128 + 25;
	private static final int EXIT_NOT_EXECUTABLE = 126;
	private static final int EXIT_NOT_FOUND = 127;

	private final String typstBin;
	private final int maxPdfBytes;

	TypstRunner(String typstBin, int maxPdfBytes) {
		this.typstBin = typstBin;
		this.maxPdfBytes = maxPdfBytes;
	}

	/** One {@code typst compile}; {@code scale} null means no scale input. */
	CompileResult run(Path workDir, Double scale, long deadline) throws IOException, InterruptedException {
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
		String fontPath = System.getenv("TYPST_FONT_PATH");
		if (fontPath != null && !fontPath.isBlank()) {
			command.add("--font-path");
			command.add(fontPath);
		}
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
}
