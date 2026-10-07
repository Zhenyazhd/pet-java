package com.jobsearch.latexworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompilerTest {

	private static final int MAX_PDF_BYTES = 10 * 1024 * 1024;

	private final Compiler compiler = compiler(Duration.ofSeconds(5), MAX_PDF_BYTES);

	@BeforeEach
	void requireTectonic() {
		TestSupport.assumeTectonicInstalled();
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
		assertTrue(failed.log().contains("Undefined control sequence"), failed.log());
	}

	@Test
	void stopsEndlessDocumentAndKillsTectonic() throws Exception {
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
	void removesWorkDirectoryEveryTime() throws Exception {
		long before = countWorkDirectories();

		compiler.compile(TestSupport.warmupSource());
		compiler.compile(TestSupport.BROKEN_DOCUMENT);

		assertEquals(before, countWorkDirectories());
	}

	private static Compiler compiler(Duration timeout, int maxPdfBytes) {
		return new Compiler("tectonic", timeout, maxPdfBytes);
	}

	private static long countWorkDirectories() throws IOException {
		try (Stream<Path> entries = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
			return entries.filter(path -> path.getFileName().toString().startsWith("latex-worker-")).count();
		}
	}
}
