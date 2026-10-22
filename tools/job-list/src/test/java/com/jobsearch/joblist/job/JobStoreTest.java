package com.jobsearch.joblist.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jobsearch.joblist.TestData;
import com.jobsearch.joblist.TestcontainersConfiguration;
import com.jobsearch.joblist.source.SourceName;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class JobStoreTest {

	@Autowired
	private JobStore store;
	@Autowired
	private JobRepository jobs;
	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		TestData.reset(jdbc);
	}

	@Test
	void sameVacancyFromAnotherBoardAndTheSameBoardIdAreStoredOnce() {
		Job first = TestData.job(SourceName.HELLOWORK, "Python Developer");
		Job otherBoard = copy(first, SourceName.WTTJ, "other-id");
		Job sameId = copy(first, SourceName.HELLOWORK, first.getExternalId());
		sameId.setTitle("A different title, so only the board id can tell it is known");

		int saved = store.saveNew(List.of(first, otherBoard, sameId), Instant.now());

		assertEquals(1, saved);
		assertEquals(1, jobs.count());
	}

	@Test
	void countryCodesAreStoredInUpperCaseAndPostingsOlderThanRetentionAreSkipped() {
		Job lower = TestData.job(SourceName.WTTJ, "Lower");
		lower.setCountries(List.of("fr"));
		Job stale = TestData.job(SourceName.WTTJ, "Stale");
		stale.setPostedAt(Instant.now().minus(Duration.ofDays(120)));

		int saved = store.saveNew(List.of(lower, stale), Instant.now());

		assertEquals(1, saved);
		assertEquals(List.of("FR"), jobs.findAll().getFirst().getCountries());
	}

	private static Job copy(Job original, SourceName source, String externalId) {
		Job copy = TestData.job(source, original.getTitle());
		copy.setExternalId(externalId);
		copy.setCompany(original.getCompany());
		copy.setCities(original.getCities());
		return copy;
	}
}
