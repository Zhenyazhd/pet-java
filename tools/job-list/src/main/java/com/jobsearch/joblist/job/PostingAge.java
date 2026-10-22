package com.jobsearch.joblist.job;

import java.time.Duration;
import java.time.Instant;

/**
 * The hard limit on the age of a vacancy ({@code job-list.max-posting-age-days}).
 *
 * <p>A job is as old as its {@code posted_at}; a posting without a date is as old as its {@code fetched_at} (it is new when
 * it is first seen, and ages from then). A date alone counts as the START of that day in UTC (see {@code JsonFields.instant}),
 * so a posting dated D is never treated as younger than it is. A job is within the limit when its date is not before
 * {@code now - days}.
 */
public final class PostingAge {

	private PostingAge() {
	}

	/** The oldest date still within the limit. */
	public static Instant cutoff(Instant now, int maxDays) {
		return now.minus(Duration.ofDays(maxDays));
	}

	/** At ingest: a known posting date older than the cutoff is out; no date means new in this run, so in. */
	public static boolean tooOldToFetch(Instant postedAt, Instant cutoff) {
		return postedAt != null && postedAt.isBefore(cutoff);
	}
}
