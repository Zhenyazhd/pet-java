package com.jobsearch.joblist.run;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * How many days back to ask a source for postings: the days since it last worked (or the first-run days), but never more
 * than the age limit, rounded UP to a window sources accept. Rounding up keeps every posting that is still within the limit
 * in reach (a limit of 2 asks for 3 days, not 1); the ingest filter enforces the exact limit afterwards.
 */
final class LookbackWindow {

	private static final List<Integer> WINDOWS = List.of(1, 3, 7, 14, 30);

	private LookbackWindow() {
	}

	static int days(Instant lastSuccess, Instant now, int firstRunDays, int maxPostingAgeDays) {
		long needed = lastSuccess == null ? firstRunDays : Math.max(1, (Duration.between(lastSuccess, now).toHours() + 23) / 24);
		long target = Math.min(needed, maxPostingAgeDays);
		return WINDOWS.stream().filter(window -> window >= target).findFirst().orElse(WINDOWS.getLast());
	}
}
