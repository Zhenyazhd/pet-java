package com.jobsearch.latexworker;

/** Outcome of one compilation; {@link CompileHandler} maps each case to an HTTP status. */
sealed interface CompileResult {

	record Ok(byte[] pdf) implements CompileResult {
	}

	/** The document itself is broken; retrying will not help. */
	record Failed(String log) implements CompileResult {
	}

	record TimedOut() implements CompileResult {
	}
}
