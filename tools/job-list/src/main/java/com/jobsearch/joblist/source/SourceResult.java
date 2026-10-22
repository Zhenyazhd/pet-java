package com.jobsearch.joblist.source;

import com.jobsearch.joblist.job.Job;
import java.math.BigDecimal;
import java.util.List;

/**
 * What one source returned: the usable jobs, how many items the actor delivered, how many of those could not be
 * mapped, and what the run cost. {@code error} is null on success.
 */
public record SourceResult(
		List<Job> jobs,
		int fetched,
		int dropped,
		BigDecimal costUsd,
		String apifyRunId,
		String error
) {

	public static SourceResult failed(String apifyRunId, BigDecimal costUsd, String error) {
		return new SourceResult(List.of(), 0, 0, costUsd, apifyRunId, error);
	}

	public boolean ok() {
		return error == null;
	}
}
