package com.jobsearch.pdfworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PageMetricsTest {

	private final Compiler compiler = new Compiler("typst", Duration.ofSeconds(30), 10 * 1024 * 1024);

	@Test
	void fillIsShareOfTextAreaBetweenFirstTopAndLastBaseline() {
		assertEquals(0.5, PageMetrics.fill(10, 400, 800), 1e-9);
		assertEquals(1, PageMetrics.fill(10, 900, 800), 1e-9);
		assertEquals(0, PageMetrics.fill(Double.MAX_VALUE, -Double.MAX_VALUE, 800), 1e-9);
		assertEquals(0, PageMetrics.fill(500, 600, 800), 1e-9);
	}

	@Test
	void unreadablePdfIsAnIoError() {
		assertThrows(IOException.class, () -> PageMetrics.measure("not a pdf".getBytes()));
	}

	@Test
	void shortDocumentIsOnePageAndMostlyEmpty() throws Exception {
		PageMetrics metrics = measure(lines(5));

		assertEquals(1, metrics.pages());
		assertTrue(metrics.fill() < 0.2, "fill=" + metrics.fill());
	}

	@Test
	void lastLineThatStillFitsMakesTheOnePageFull() throws Exception {
		TestSupport.assumeTypstInstalled();
		int fits = 1;
		int overflows = 150;
		while (overflows - fits > 1) {
			int middle = (fits + overflows) / 2;
			if (measure(lines(middle)).pages() == 1) {
				fits = middle;
			}
			else {
				overflows = middle;
			}
		}

		PageMetrics full = measure(lines(fits));
		PageMetrics over = measure(lines(overflows));

		assertEquals(1, full.pages());
		assertTrue(full.fill() > 0.9, "fill=" + full.fill());
		assertEquals(2, over.pages());
		assertTrue(over.fill() < 0.1, "fill of the spilled page=" + over.fill());
	}

	private PageMetrics measure(String source) throws Exception {
		TestSupport.assumeTypstInstalled();
		CompileResult.Ok ok = assertInstanceOf(CompileResult.Ok.class, compiler.compile(source));
		return ok.metrics();
	}

	private static String lines(int count) {
		return TestSupport.scalableDocument(count);
	}
}
