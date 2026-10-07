package com.jobsearch.latexworker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Runs tectonic on a LaTeX source in a throwaway directory.
 *
 * <p>{@code --untrusted} disables shell escape and similar features; {@code --only-cached} forbids
 * downloading packages, so every package must already be in the local cache (see warmup.tex).
 */
final class Compiler {

	private static final int MAX_LOG_CHARS = 4000;

	private final String tectonicBin;
	private final Duration timeout;
	private final int maxPdfBytes;

	Compiler(String tectonicBin, Duration timeout, int maxPdfBytes) {
		this.tectonicBin = tectonicBin;
		this.timeout = timeout;
		this.maxPdfBytes = maxPdfBytes;
	}

	CompileResult compile(String source) throws IOException, InterruptedException {
		Path workDir = Files.createTempDirectory("latex-worker-");
		try {
			Files.writeString(workDir.resolve("main.tex"), source, StandardCharsets.UTF_8);
			// Output goes to a file, not a pipe: reading a pipe blocks until the process exits,
			// which would make the timeout below unreachable.
			Path logFile = workDir.resolve("compile.log");
			Process process = new ProcessBuilder(tectonicBin, "--untrusted", "--only-cached", "main.tex")
					.directory(workDir.toFile())
					.redirectErrorStream(true)
					.redirectOutput(logFile.toFile())
					.start();

			if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
				process.descendants().forEach(ProcessHandle::destroyForcibly);
				process.destroyForcibly().waitFor();
				return new CompileResult.TimedOut();
			}

			Path pdf = workDir.resolve("main.pdf");
			if (process.exitValue() != 0 || !Files.exists(pdf)) {
				return new CompileResult.Failed(tail(Files.readString(logFile, StandardCharsets.UTF_8)));
			}
			if (Files.size(pdf) > maxPdfBytes) {
				return new CompileResult.Failed("PDF is larger than " + maxPdfBytes + " bytes");
			}
			return new CompileResult.Ok(Files.readAllBytes(pdf));
		}
		finally {
			deleteRecursively(workDir);
		}
	}

	private static String tail(String log) {
		String trimmed = log.strip();
		if (trimmed.isEmpty()) {
			return "LaTeX compilation failed with no output";
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
