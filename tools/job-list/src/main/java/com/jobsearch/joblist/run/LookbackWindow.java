package com.jobsearch.joblist.run;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** How many days back to ask a source for postings: since it last worked, rounded up to a window sources accept. */
final class LookbackWindow {

	private static final List<Integer> WINDOWS = List.of(1, 3, 7, 14, 30);

	private LookbackWindow() {
	}

	static int days(Instant lastSuccess, Instant now, int firstRunDays) {
		if (lastSuccess == null) {
			return firstRunDays;
		}
		long needed = Math.max(1, (Duration.between(lastSuccess, now).toHours() + 23) / 24);
		return WINDOWS.stream().filter(window -> window >= needed).findFirst().orElse(WINDOWS.getLast());
	}
}
