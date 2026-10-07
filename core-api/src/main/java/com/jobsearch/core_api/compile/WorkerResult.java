package com.jobsearch.core_api.compile;

/** What {@link LatexWorkerClient} got back from the worker, grouped by what the dispatcher must do next. */
sealed interface WorkerResult {

	record Pdf(byte[] bytes) implements WorkerResult {
	}

	/** The worker refused the document itself (e.g. it does not compile); retrying will not help. */
	record Rejected(String code, String message) implements WorkerResult {
	}

	/** Every compile slot on the worker is taken; try again after {@code retryAfterSeconds}. */
	record Busy(long retryAfterSeconds) implements WorkerResult {
	}

	/** Timeout, worker error or network failure; worth retrying. */
	record Unavailable(String reason) implements WorkerResult {
	}
}
