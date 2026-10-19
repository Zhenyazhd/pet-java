package com.jobsearch.joblist.run;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LookbackWindowTest {

	private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

	@Test
	void withTheDefaultLimitASourceIsNeverAskedForMoreThanThreeDays() {
		assertEquals(3, LookbackWindow.days(null, NOW, 3, 3), "first run");
		assertEquals(1, LookbackWindow.days(NOW.minus(Duration.ofHours(5)), NOW, 3, 3));
		assertEquals(1, LookbackWindow.days(NOW.minus(Duration.ofHours(24)), NOW, 3, 3));
		assertEquals(3, LookbackWindow.days(NOW.minus(Duration.ofHours(25)), NOW, 3, 3));
		assertEquals(3, LookbackWindow.days(NOW.minus(Duration.ofDays(5)), NOW, 3, 3));
		assertEquals(3, LookbackWindow.days(NOW.minus(Duration.ofDays(60)), NOW, 3, 3), "after a long gap");
	}

	@Test
	void aLongGapAsksForTheSmallestWindowThatCoversTheLimit() {
		int[][] limitToWindow = { { 1, 1 }, { 2, 3 }, { 3, 3 }, { 4, 7 }, { 5, 7 }, { 7, 7 }, { 8, 14 }, { 14, 14 }, { 30, 30 } };
		for (int[] row : limitToWindow) {
			assertEquals(row[1], LookbackWindow.days(NOW.minus(Duration.ofDays(90)), NOW, 3, row[0]), "limit " + row[0]);
		}
	}

	@Test
	void roundingUpNeverSkipsAPostingStillWithinTheLimit() {
		// limit 2, last success 30 h ago: 2 days are needed, so the window is 3 (not 1)
		assertEquals(3, LookbackWindow.days(NOW.minus(Duration.ofHours(30)), NOW, 3, 2));
		assertEquals(1, LookbackWindow.days(NOW.minus(Duration.ofHours(30)), NOW, 3, 1));
		assertEquals(7, LookbackWindow.days(NOW.minus(Duration.ofDays(4)), NOW, 3, 5));
	}

	@Test
	void theFirstRunWindowIsLimitedAndRoundedUpToo() {
		assertEquals(1, LookbackWindow.days(null, NOW, 7, 1));
		assertEquals(7, LookbackWindow.days(null, NOW, 7, 30));
		assertEquals(7, LookbackWindow.days(null, NOW, 14, 5));
		assertEquals(3, LookbackWindow.days(null, NOW, 3, 30));
		assertEquals(3, LookbackWindow.days(null, NOW, 2, 30));
	}

	@Test
	void aSmallGapKeepsItsOwnWindowUnderALargerLimit() {
		assertEquals(1, LookbackWindow.days(NOW.minus(Duration.ofHours(10)), NOW, 3, 30));
		assertEquals(7, LookbackWindow.days(NOW.minus(Duration.ofDays(5)), NOW, 3, 30));
		assertEquals(30, LookbackWindow.days(NOW.minus(Duration.ofDays(90)), NOW, 3, 30));
	}
}
