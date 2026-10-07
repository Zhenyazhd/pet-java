package com.jobsearch.core_api.jobs;

import java.time.Duration;

/** What a {@link JobHandler} reports back; {@link JobDispatcher} turns it into the job's next status. */
public sealed interface JobOutcome {

	/** {@code result} is stored on the job as-is and may be null. */
	record Succeeded(String result) implements JobOutcome {
	}

	/** Permanent failure; {@code message} is shown to the user. */
	record Failed(String code, String message) implements JobOutcome {
	}

	/** Worth another try; counts as an attempt. */
	record Retry(String reason) implements JobOutcome {
	}

	/** The service behind this job type is saturated: requeue without counting an attempt and pause the type. */
	record Busy(Duration retryAfter) implements JobOutcome {
	}
}
