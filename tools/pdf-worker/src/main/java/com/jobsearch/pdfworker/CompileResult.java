package com.jobsearch.pdfworker;

/** Outcome of one compilation; {@link CompileHandler} maps each case to an HTTP status. */
sealed interface CompileResult {

	/**
	 * {@code metrics} is null when the PDF could not be measured; the PDF itself is still good.
	 * {@code scale} is the layout scale the PDF was compiled with in fit mode, null otherwise.
	 */
	record Ok(byte[] pdf, PageMetrics metrics, Double scale) implements CompileResult {
	}

	/** The document itself is broken; retrying will not help. */
	record Failed(String log) implements CompileResult {
	}

	record TimedOut() implements CompileResult {
	}
}
