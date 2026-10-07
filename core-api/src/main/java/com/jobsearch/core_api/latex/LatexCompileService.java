package com.jobsearch.core_api.latex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Compiles LaTeX to PDF via native tectonic when available,
 * otherwise falls back to Docker (slower on Apple Silicon).
 *
 * <p>Trust model: source is rendered from the user-controlled structured resume. Compiles always pass
 * {@code tectonic --untrusted} so known-insecure engine features (e.g. shell escape)
 * stay disabled. Work runs in a per-request temp directory that is deleted afterward.
 */
@Service
public class LatexCompileService {

	private static final Logger log = LoggerFactory.getLogger(LatexCompileService.class);
	private static final Duration TIMEOUT = Duration.ofSeconds(120);
	private static final String DOCKER_IMAGE = "dxjoke/tectonic-docker";
	private static final Path HOST_TECTONIC_CACHE =
			Path.of(System.getProperty("user.home"), ".cache", "job-search-tectonic");

	private final Optional<String> tectonicBinary;
	private final boolean dockerAvailable;

	public LatexCompileService() {
		this.tectonicBinary = resolveTectonicBinary();
		this.dockerAvailable = tectonicBinary.isEmpty() && commandExists("docker");
		if (tectonicBinary.isPresent()) {
			log.info("LaTeX compiler ready: native tectonic={}", tectonicBinary.get());
		}
		else if (dockerAvailable) {
			log.info("LaTeX compiler ready: Docker fallback image={}", DOCKER_IMAGE);
		}
		else {
			log.warn("No LaTeX compiler detected at startup (tectonic/docker missing)");
		}
	}

	public byte[] compile(String source) {
		Path workDir = null;
		try {
			workDir = Files.createTempDirectory("latex-compile-");
			Path texFile = workDir.resolve("main.tex");
			Files.writeString(texFile, source, StandardCharsets.UTF_8);

			CompileResult result = runCompiler(workDir);
			if (result.exitCode() != 0) {
				throw new IllegalArgumentException(trimLog(result.log()));
			}

			Path pdf = workDir.resolve("main.pdf");
			if (!Files.exists(pdf)) {
				log.error("LaTeX compiler exited 0 but produced no PDF. Log:\n{}", trimLog(result.log()));
				throw new IllegalStateException("PDF compilation failed due to a server error. Please try again later.");
			}
			byte[] bytes = Files.readAllBytes(pdf);
			log.info("LaTeX compile succeeded pdfBytes={}", bytes.length);
			return bytes;
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to prepare LaTeX compilation workspace", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("LaTeX compilation was interrupted", ex);
		}
		finally {
			cleanup(workDir);
		}
	}

	private CompileResult runCompiler(Path workDir) throws IOException, InterruptedException {
		if (tectonicBinary.isPresent()) {
			log.info("Compiling LaTeX with native tectonic --untrusted: {}", tectonicBinary.get());
			return execute(List.of(tectonicBinary.get(), "--untrusted", "main.tex"), workDir);
		}
		if (dockerAvailable) {
			Files.createDirectories(HOST_TECTONIC_CACHE);
			log.warn(
					"Compiling LaTeX via Docker amd64 emulation (slow on Apple Silicon). "
							+ "Install native tectonic for speed: brew install tectonic"
			);
			return execute(List.of(
					"docker", "run", "--rm",
					"--platform", "linux/amd64",
					"-v", workDir.toAbsolutePath() + ":/data",
					"-v", HOST_TECTONIC_CACHE.toAbsolutePath() + ":/root/.cache/Tectonic",
					"-w", "/data",
					DOCKER_IMAGE, "tectonic", "--untrusted", "main.tex"
			), workDir);
		}
		log.error("No LaTeX compiler found (tectonic/docker missing)");
		throw new IllegalStateException("PDF compiler is not available on this server. Please try again later.");
	}

	private CompileResult execute(List<String> command, Path workDir) throws IOException, InterruptedException {
		// Output goes to a file, not a pipe: reading a pipe blocks until the process exits,
		// which would make the waitFor timeout below unreachable.
		Path logFile = workDir.resolve("compile.log");
		Process process = new ProcessBuilder(command)
				.directory(workDir.toFile())
				.redirectErrorStream(true)
				.redirectOutput(logFile.toFile())
				.start();
		boolean finished = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
		if (!finished) {
			process.destroyForcibly();
			throw new IllegalStateException("LaTeX compilation timed out after " + TIMEOUT.toSeconds() + "s");
		}
		return new CompileResult(process.exitValue(), Files.readString(logFile, StandardCharsets.UTF_8));
	}

	private static Optional<String> resolveTectonicBinary() {
		if (commandExists("tectonic")) {
			return Optional.of("tectonic");
		}
		for (Path candidate : List.of(
				Path.of("/opt/homebrew/bin/tectonic"),
				Path.of("/usr/local/bin/tectonic")
		)) {
			if (Files.isExecutable(candidate)) {
				return Optional.of(candidate.toString());
			}
		}
		return Optional.empty();
	}

	private static boolean commandExists(String command) {
		try {
			Process process = new ProcessBuilder(command, "--version")
					.redirectErrorStream(true)
					.start();
			boolean finished = process.waitFor(5, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				return false;
			}
			return process.exitValue() == 0 || process.exitValue() == 1;
		}
		catch (IOException | InterruptedException ex) {
			if (ex instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return false;
		}
	}

	private static String trimLog(String log) {
		if (log == null || log.isBlank()) {
			return "LaTeX compilation failed with no output";
		}
		String trimmed = log.strip();
		if (trimmed.length() > 4000) {
			return trimmed.substring(trimmed.length() - 4000);
		}
		return trimmed;
	}

	private static void cleanup(Path workDir) {
		if (workDir == null || !Files.exists(workDir)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(workDir)) {
			walk.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				}
				catch (IOException ignored) {
					// best-effort cleanup
				}
			});
		}
		catch (IOException ignored) {
			// best-effort cleanup
		}
	}

	private record CompileResult(int exitCode, String log) {
	}
}
