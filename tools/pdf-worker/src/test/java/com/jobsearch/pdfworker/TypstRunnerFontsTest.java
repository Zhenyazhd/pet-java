package com.jobsearch.pdfworker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The fonts of the image reach the compiler: without the font path a document asking for them gets another font. */
class TypstRunnerFontsTest {

	private static final Path FONTS = Path.of("fonts");
	private static final String SOURCE = "#set text(font: \"Source Sans 3\")\nHello";

	@TempDir
	Path dir;

	@BeforeEach
	void requireTypstAndFonts() {
		TestSupport.assumeTypstInstalled();
		assertTrue(Files.isDirectory(FONTS), "fonts/ must be next to the pdf-worker sources");
	}

	private boolean embedsSourceSans(String fontPath) throws Exception {
		Files.writeString(dir.resolve("main.typ"), SOURCE);
		CompileResult result = new TypstRunner("typst", 10 * 1024 * 1024, fontPath)
				.run(dir, null, System.nanoTime() + 30_000_000_000L);
		CompileResult.Ok ok = assertInstanceOf(CompileResult.Ok.class, result);
		return new String(ok.pdf(), StandardCharsets.ISO_8859_1).contains("SourceSans3");
	}

	@Test
	void fontPathMakesTheImageFontsAvailable() throws Exception {
		assertTrue(embedsSourceSans(FONTS.toAbsolutePath().toString()));
	}

	@Test
	void withoutTheFontPathTheFontIsMissing() throws Exception {
		assertFalse(embedsSourceSans(null));
	}
}
