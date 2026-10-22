package com.jobsearch.joblist.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobsearch.joblist.TestData;
import com.jobsearch.joblist.TestcontainersConfiguration;
import com.jobsearch.joblist.job.Job;
import com.jobsearch.joblist.job.JobRepository;
import com.jobsearch.joblist.profile.ProfileDtos.ProfileRequest;
import com.jobsearch.joblist.profile.SearchProfileService;
import com.jobsearch.joblist.source.Region;
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
class JanitorTest {

	@Autowired
	private Janitor janitor;
	@Autowired
	private SearchProfileService profileService;
	@Autowired
	private RunRepository runs;
	@Autowired
	private JobRepository jobs;
	@Autowired
	private JdbcTemplate jdbc;

	private long profileId;

	@BeforeEach
	void setUp() {
		TestData.reset(jdbc);
		profileId = profileService.create("core-api", new ProfileRequest("owner", "Daily", "Anything.", List.of("python developer"),
				List.of(new Region("FR", null)), List.of(SourceName.HELLOWORK), null)).id();
	}

	@Test
	void runThatKeptItsLeasePastTheDeadlineIsReleased() {
		Run run = runs.save(Run.queued(profileId));
		run.start(Instant.now().minusSeconds(60));
		runs.save(run);

		janitor.expireLostRuns();

		Run released = runs.findById(run.getId()).orElseThrow();
		assertEquals(RunStatus.FAILED, released.getStatus());
		assertTrue(released.getError().contains("lost"));
	}

	@Test
	void runStillMarkedRunningWhenTheServiceStartsWasCutOffByTheRestart() {
		Run run = runs.save(Run.queued(profileId));
		run.start(Instant.now().plusSeconds(3600));
		runs.save(run);

		janitor.releaseInterruptedRuns();

		assertEquals(RunStatus.FAILED, runs.findById(run.getId()).orElseThrow().getStatus());
	}

	@Test
	void oldRunStartsAreDeletedAndRecentOnesStay() {
		jdbc.update("insert into client_run_start (client, owner_ref, created_at) values ('core-api', 'o', now() - interval '4 days')");
		jdbc.update("insert into client_run_start (client, owner_ref, created_at) values ('core-api', 'o', now() - interval '2 hours')");

		janitor.deleteOldData();

		assertEquals(1, jdbc.queryForObject("select count(*) from client_run_start", Integer.class));
	}

	@Test
	void oldJobsAreDeletedAndRecentOnesStay() {
		Job old = TestData.job(SourceName.WTTJ, "Old");
		old.setFingerprint("old");
		old.setFetchedAt(Instant.now().minus(Duration.ofDays(61)));
		Job recent = TestData.job(SourceName.WTTJ, "Recent");
		recent.setFingerprint("recent");
		recent.setFetchedAt(Instant.now().minus(Duration.ofDays(2)));
		jobs.saveAll(List.of(old, recent));

		janitor.deleteOldData();

		assertEquals(List.of("Recent"), jobs.findAll().stream().map(Job::getTitle).toList());
	}
}
