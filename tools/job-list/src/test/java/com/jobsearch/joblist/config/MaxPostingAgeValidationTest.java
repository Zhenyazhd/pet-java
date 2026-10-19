package com.jobsearch.joblist.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobsearch.joblist.TestProperties;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class MaxPostingAgeValidationTest {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void theLimitMustBeBetweenOneAndThirtyDays() {
		JobListProperties ok = TestProperties.defaults();
		assertEquals(3, ok.maxPostingAgeDays());
		assertTrue(validator.validate(ok).isEmpty());
		assertTrue(violations(0), "0 is refused");
		assertTrue(violations(31), "31 is refused");
		assertTrue(violations(-1));
		assertTrue(!violations(1) && !violations(30));
	}

	private boolean violations(int days) {
		JobListProperties p = TestProperties.defaults();
		JobListProperties changed = new JobListProperties(p.clients(), p.apify(), p.llm(), p.sources(), p.maxItemsPerSource(),
				p.firstRunLookbackDays(), p.maxRankedPerRun(), p.maxDescriptionChars(), p.mapperDropThreshold(), p.retentionDays(),
				p.runRetentionDays(), p.rankWindowDays(), days, p.maxProfilesPerOwner(), p.maxProfilesPerClient(),
				p.maxRunsPerOwnerPerDay(), p.maxRunsPerClientPerDay(), p.maxRankRunsPerOwnerPerDay(), p.maxRankRunsPerClientPerDay());
		return validator.validate(changed).stream().anyMatch(v -> v.getPropertyPath().toString().equals("maxPostingAgeDays"));
	}
}
