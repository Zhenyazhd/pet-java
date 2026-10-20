package com.jobsearch.pdfworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompilerTest {

	@TempDir
	Path dir;

	private static final int MAX_PDF_BYTES = 10 * 1024 * 1024;

	private final Compiler compiler = compiler(Duration.ofSeconds(5), MAX_PDF_BYTES);

	@BeforeEach
	void requireTypst() {
		TestSupport.assumeTypstInstalled();
	}

	@Test
	void compilesWarmupDocument() throws Exception {
		CompileResult result = compiler.compile(TestSupport.warmupSource());

		CompileResult.Ok ok = assertInstanceOf(CompileResult.Ok.class, result);
		assertEquals("%PDF", new String(ok.pdf(), 0, 4, StandardCharsets.US_ASCII));
	}

	@Test
	void reportsBrokenDocumentWithLog() throws Exception {
		CompileResult result = compiler.compile(TestSupport.BROKEN_DOCUMENT);

		CompileResult.Failed failed = assertInstanceOf(CompileResult.Failed.class, result);
		assertTrue(failed.log().contains("unknown variable"), failed.log());
	}

	@Test
	void stopsEndlessDocumentAndKillsTypst() throws Exception {
		Compiler quick = compiler(Duration.ofSeconds(2), MAX_PDF_BYTES);

		CompileResult result = quick.compile(TestSupport.ENDLESS_DOCUMENT);

		assertInstanceOf(CompileResult.TimedOut.class, result);
		assertEquals(0, ProcessHandle.current().descendants().count());
	}

	@Test
	void rejectsPdfOverSizeLimit() throws Exception {
		Compiler tiny = compiler(Duration.ofSeconds(30), 100);

		CompileResult result = tiny.compile(TestSupport.warmupSource());

		CompileResult.Failed failed = assertInstanceOf(CompileResult.Failed.class, result);
		assertTrue(failed.log().startsWith("PDF is larger than"), failed.log());
	}

	@Test
	void packageImportsFailBecauseTheDownloadIsBlocked() throws Exception {
		CompileResult result = compiler.compile("#import \"@preview/cetz:0.3.2\": canvas\nhi");

		CompileResult.Failed failed = assertInstanceOf(CompileResult.Failed.class, result);
		assertTrue(failed.log().contains("failed to download package"), failed.log());
	}

	@Test
	void stopsAnUnboundedPdfWithTheFileSizeLimit() throws Exception {
		// ~3000 pages are far more than 200 kB; the ulimit stops typst while it writes the PDF.
		Compiler capped = compiler(Duration.ofSeconds(60), 200_000);

		CompileResult result = capped.compile("#for i in range(3000) [ #i #pagebreak() ]");

		CompileResult.Failed failed = assertInstanceOf(CompileResult.Failed.class, result);
		assertEquals("PDF is larger than 200000 bytes", failed.log());
	}

	@Test
	void childGetsOnlyAMinimalEnvironment() throws Exception {
		Path envFile = dir.resolve("env");
		Compiler spy = compilerScript("env > '" + envFile + "'\nexit 1\n");

		spy.compile("x");

		List<String> keys = Files.readAllLines(envFile).stream().map(line -> line.split("=", 2)[0]).toList();
		assertTrue(keys.contains("HTTPS_PROXY") && keys.contains("HOME") && keys.contains("PATH"), keys.toString());
		assertTrue(Set.of("PATH", "HOME", "XDG_CACHE_HOME", "HTTPS_PROXY", "HTTP_PROXY", "ALL_PROXY", "https_proxy",
				"http_proxy", "all_proxy", "PWD", "OLDPWD", "SHLVL", "_").containsAll(keys), keys.toString());
	}

	@Test
	void fileSizeSignalIsAPermanentSizeError() throws Exception {
		CompileResult result = compilerScript("kill -XFSZ $$\n").compile("x");

		CompileResult.Failed failed = assertInstanceOf(CompileResult.Failed.class, result);
		assertTrue(failed.log().startsWith("PDF is larger than"), failed.log());
	}

	@Test
	void anyOtherSignalIsARetryableWorkerError() throws Exception {
		Compiler killed = compilerScript("kill -KILL $$\n");

		IOException ex = assertThrows(IOException.class, () -> killed.compile("x"));
		assertTrue(ex.getMessage().contains("signal 9"), ex.getMessage());
		Compiler crashed = compilerScript("kill -SEGV $$\n");
		assertThrows(IOException.class, () -> crashed.compile("x"));
	}

	@Test
	void removesWorkDirectoryEveryTime() throws Exception {
		long before = countWorkDirectories();

		compiler.compile(TestSupport.warmupSource());
		compiler.compile(TestSupport.BROKEN_DOCUMENT);

		assertEquals(before, countWorkDirectories());
	}

	/** A compiler whose "typst" is a shell script with the given body. */
	private Compiler compilerScript(String body) throws IOException {
		Path script = Files.createTempFile(dir, "fake-typst", ".sh");
		Files.writeString(script, "#!/bin/sh\n" + body);
		Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
		return new Compiler(script.toString(), Duration.ofSeconds(10), MAX_PDF_BYTES);
	}

	@Test
	void missingTypstBinaryIsAWorkerFaultNotADocumentError() {
		Compiler broken = new Compiler("/nonexistent/typst", Duration.ofSeconds(5), MAX_PDF_BYTES);

		assertThrows(IOException.class, () -> broken.compile("hello", false));
	}

	private static Compiler compiler(Duration timeout, int maxPdfBytes) {
		return new Compiler("typst", timeout, maxPdfBytes);
	}

	private static long countWorkDirectories() throws IOException {
		try (Stream<Path> entries = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
			return entries.filter(path -> path.getFileName().toString().startsWith("pdf-worker-")).count();
		}
	}
}
