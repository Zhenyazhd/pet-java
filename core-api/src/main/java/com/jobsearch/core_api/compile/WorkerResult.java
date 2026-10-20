package com.jobsearch.core_api.compile;

/** What {@link PdfWorkerClient} got back from the worker; {@link CompileJobHandler} maps it to a JobOutcome. */
public sealed interface WorkerResult {

	/** {@code layout} is null when the worker did not report page metrics. */
	record Pdf(byte[] bytes, PageLayout layout) implements WorkerResult {

		public Pdf(byte[] bytes) {
			this(bytes, null);
		}
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
