package com.jobsearch.core_api.jobs;

import java.time.Duration;

/** The part of a job type that is not shared: how to run it and how much of it to run at once. */
public interface JobHandler {

	JobType type();

	/** Jobs of this type one core-api instance runs at the same time. */
	int concurrency();

	int maxAttempts();

	/** How long a claimed job may run before {@link JobJanitor} assumes its instance died and takes it back. */
	Duration lease();

	/**
	 * A disabled type is not claimed. Jobs of it that are already queued are failed by {@link JobJanitor} once its
	 * queue timeout passes, so nothing waits for the type to be switched on again.
	 */
	default boolean disabled() {
		return false;
	}

	/**
	 * Runs one claimed job. Called outside any transaction; every expected failure is returned as an
	 * outcome rather than thrown.
	 */
	JobOutcome run(Job job);
}
