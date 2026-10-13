package com.jobsearch.atsworker;

import java.time.Duration;

/**
 * PDFBox cannot be interrupted, so the extraction polls this and gives up by itself; a thread stuck in a
 * pathological file would otherwise keep a check slot and the CPU long after the caller was told "timeout".
 */
final class Deadline {

	private final long endNanos;

	private Deadline(long endNanos) {
		this.endNanos = endNanos;
	}

	static Deadline after(Duration timeout) {
		return new Deadline(System.nanoTime() + timeout.toNanos());
	}

	void check() {
		if (System.nanoTime() - endNanos > 0) {
			throw new CheckFailure(504, "check_timeout", "Reading the PDF took too long");
		}
	}
}
