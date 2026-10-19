package com.jobsearch.joblist.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class JsonFieldsInstantTest {

	@Test
	void aDateAloneIsTheStartOfThatDayInUtcAndADateTimeWithoutZoneIsUtc() {
		assertEquals(Instant.parse("2026-10-17T00:00:00Z"), JsonFields.instant("2026-10-17"));
		assertEquals(Instant.parse("2026-10-17T08:30:00Z"), JsonFields.instant("2026-10-17T08:30:00"));
		assertEquals(Instant.parse("2026-10-17T08:30:00Z"), JsonFields.instant("2026-10-17T08:30:00Z"));
	}

	@Test
	void missingOrUnreadableDatesAreNull() {
		assertNull(JsonFields.instant(null));
		assertNull(JsonFields.instant("yesterday"));
		assertNull(JsonFields.instant(""));
	}
}
