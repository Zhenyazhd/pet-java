package com.jobsearch.joblist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobsearch.joblist.job.Job;
import com.jobsearch.joblist.job.JobRepository;
import com.jobsearch.joblist.profile.ProfileDtos.ProfileRequest;
import com.jobsearch.joblist.profile.SearchProfileService;
import com.jobsearch.joblist.rank.LlmClient;
import com.jobsearch.joblist.rank.MatchRepository;
import com.jobsearch.joblist.run.Run;
import com.jobsearch.joblist.run.RunMode;
import com.jobsearch.joblist.run.RunRepository;
import com.jobsearch.joblist.run.RunSourceRepository;
import com.jobsearch.joblist.run.RunStatus;
import com.jobsearch.joblist.run.RunWorker;
import com.jobsearch.joblist.run.UsageService;
import com.jobsearch.joblist.source.ApifyFetcher;
import com.jobsearch.joblist.source.Region;
import com.jobsearch.joblist.source.SourceName;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** "Continue ranking": rank-only runs and the unranked count, with the Apify fetcher and the model replaced by fakes. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RankOnlyRunTest {

	private static final Pattern JOB_TAG = Pattern.compile("<job id=\"(\\d+)\">");
	private static final String CORE_API = "Bearer test-token-core-api";
	private static final String BOT = "Bearer test-token-telegram-bot";

	@Autowired
	private MockMvc mvc;
	@Autowired
	private SearchProfileService profiles;
	@Autowired
	private JobRepository jobs;
	@Autowired
	private MatchRepository matches;
	@Autowired
	private RunRepository runs;
	@Autowired
	private RunSourceRepository runSources;
	@Autowired
	private RunWorker worker;
	@Autowired
	private UsageService usage;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private com.jobsearch.joblist.config.JobListProperties props;
	@Autowired
	private com.jobsearch.joblist.run.Janitor janitor;
	@Autowired
	private com.jobsearch.joblist.rank.RankCandidates rankCandidates;
	@Autowired
	private com.jobsearch.joblist.rank.JobRanker ranker;
	@MockitoBean
	private ApifyFetcher fetcher;
	@MockitoBean
	private LlmClient llm;

	private long profileId;

	@BeforeEach
	void setUp() {
		TestData.reset(jdbc);
		profileId = profiles.create("core-api", new ProfileRequest("owner", "P", "Python backend in Paris.", List.of("python developer"),
				List.of(new Region("FR", null)), List.of(SourceName.HELLOWORK), null)).id();
	}

	@Test
	void rankOnlyRunRanksTheUnrankedJobsAndNeverCallsASource() throws Exception {
		stored("A", Instant.now().minus(Duration.ofHours(2)));
		stored("B", Instant.now().minus(Duration.ofHours(3)));
		modelScores(80);
		mvc.perform(get("/v1/profiles/" + profileId + "/unranked").header("Authorization", CORE_API))
				.andExpect(status().isOk()).andExpect(jsonPath("$.count").value(2));

		mvc.perform(post("/v1/profiles/" + profileId + "/runs?mode=rank").header("Authorization", CORE_API))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.mode").value("RANK_ONLY"))
				.andExpect(jsonPath("$.status").value("QUEUED"));
		assertTrue(worker.runOnce());

		Run run = runs.findTop20ByProfileIdOrderByIdDesc(profileId).getFirst();
		assertEquals(RunMode.RANK_ONLY, run.getMode());
		assertEquals(RunStatus.SUCCEEDED, run.getStatus());
		assertEquals(0, run.getFetched());
		assertEquals(0, run.getNewJobs());
		assertEquals(2, run.getRanked());
		assertEquals(0, new BigDecimal("0.001").compareTo(run.getCostUsd()));
		assertEquals(0, runSources.findByRunIdIn(List.of(run.getId())).size());
		assertEquals(2, matches.count());
		verifyNoInteractions(fetcher);
		assertEquals(0, jdbc.queryForObject("select count(*) from spend_ledger where kind = 'APIFY'", Integer.class));
		assertEquals(0, new BigDecimal("0").compareTo(usage.current().spentUsd()), "no Apify spend");
		assertEquals(0, jdbc.queryForObject("select count(*) from client_run_start where mode = 'FULL'", Integer.class));
		mvc.perform(get("/v1/profiles/" + profileId + "/unranked").header("Authorization", CORE_API)).andExpect(jsonPath("$.count").value(0));
		mvc.perform(get("/v1/profiles/" + profileId + "/runs").header("Authorization", CORE_API))
				.andExpect(jsonPath("$[0].mode").value("RANK_ONLY"))
				.andExpect(jsonPath("$[0].sources.length()").value(0))
				.andExpect(jsonPath("$[0].ranked").value(2));
	}

	@Test
	void onlyTheStillUnrankedJobsAreSentToTheModelAndAtMostTheCapPerRun() throws Exception {
		int cap = props.maxRankedPerRun();
		for (int i = 0; i < cap + 5; i++) {
			stored("Job " + i, Instant.now().minus(Duration.ofHours(1)));
		}
		modelScores(70);
		startRank().andExpect(status().isAccepted());
		worker.runOnce();
		assertEquals(cap, runs.findTop20ByProfileIdOrderByIdDesc(profileId).getFirst().getRanked(), "max-ranked-per-run");
		mvc.perform(get("/v1/profiles/" + profileId + "/unranked").header("Authorization", CORE_API)).andExpect(jsonPath("$.count").value(5));

		startRank().andExpect(status().isAccepted());
		worker.runOnce();

		assertEquals(5, runs.findTop20ByProfileIdOrderByIdDesc(profileId).getFirst().getRanked());
		assertEquals(cap + 5, matches.count());
		assertEquals(0, jdbc.queryForObject("select count(*) from (select job_id from match group by job_id having count(*) > 1) x", Integer.class));
	}

	@Test
	void nothingToRankIsAConflictAndQueuesNothing() throws Exception {
		startRank().andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Nothing to rank"));
		assertEquals(0, runs.count());
		assertEquals(0, jdbc.queryForObject("select count(*) from client_run_start", Integer.class));
	}

	@Test
	void activeRunOfTheSameKindIsReturnedAndOfAnotherKindIsAConflict() throws Exception {
		stored("A", Instant.now());
		String first = startRank().andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
		long id = new tools.jackson.databind.ObjectMapper().readTree(first).get("id").asLong();

		startRank().andExpect(status().isAccepted()).andExpect(jsonPath("$.id").value(id));
		mvc.perform(post("/v1/profiles/" + profileId + "/runs").header("Authorization", CORE_API))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("A ranking run is already in progress for this profile"));
		assertEquals(1, runs.count());
		assertEquals(1, jdbc.queryForObject("select count(*) from client_run_start", Integer.class));
		runs.deleteAll();
		mvc.perform(post("/v1/profiles/" + profileId + "/runs").header("Authorization", CORE_API)).andExpect(status().isAccepted());
		startRank().andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("A full run is already in progress for this profile"));
	}

	@Test
	void rankOnlyRunsHaveTheirOwnDailyCapAndDoNotUseUpTheFullRunLimit() throws Exception {
		stored("A", Instant.now());
		int ownerCap = props.maxRankRunsPerOwnerPerDay();
		for (int i = 0; i < ownerCap; i++) {
			jdbc.update("insert into client_run_start (client, owner_ref, mode, created_at) values ('core-api', 'owner', 'RANK_ONLY', now())");
		}

		startRank().andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(ownerCap + " ranking runs")));
		assertEquals(0, runs.count());
		// a full run is still allowed: the rank-only starts are not counted against it (owner limit)
		mvc.perform(post("/v1/profiles/" + profileId + "/runs").header("Authorization", CORE_API))
				.andExpect(status().isAccepted()).andExpect(jsonPath("$.mode").value("FULL"));
	}

	@Test
	void rankOnlyRunsHaveAPerClientCapToo() throws Exception {
		stored("A", Instant.now());
		for (int i = 0; i < props.maxRankRunsPerClientPerDay(); i++) {
			jdbc.update("insert into client_run_start (client, owner_ref, mode, created_at) values ('core-api', ?, 'RANK_ONLY', now())", "other-" + i);
		}

		startRank().andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("This client has already started")));
		assertEquals(0, runs.count());
		mvc.perform(post("/v1/profiles/" + profileId + "/runs").header("Authorization", CORE_API)).andExpect(status().isAccepted());
	}

	@Test
	void exhaustedModelBudgetRefusesTheStartWithoutTakingACapSlot() throws Exception {
		stored("A", Instant.now());
		jdbc.update("insert into spend_ledger (kind, status, cost_usd, created_at) values ('LLM', 'SUCCEEDED', ?, now())", props.llm().monthlyBudgetUsd());

		startRank().andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("The monthly model budget is used up"));

		assertEquals(0, runs.count());
		assertEquals(0, jdbc.queryForObject("select count(*) from client_run_start", Integer.class));
	}

	@Test
	void modelFailureBeforeAnythingIsRankedFailsTheRunAndAfterwardsItIsPartial() throws Exception {
		stored("A", Instant.now());
		when(llm.complete(anyString(), anyString(), anyInt())).thenReturn(new LlmClient.Completion(null, BigDecimal.ZERO, "The model provider is rate limiting the requests"));
		startRank().andExpect(status().isAccepted());
		worker.runOnce();
		Run failed = runs.findTop20ByProfileIdOrderByIdDesc(profileId).getFirst();
		assertEquals(RunStatus.FAILED, failed.getStatus());
		assertEquals("The model provider is rate limiting the requests", failed.getError());
	}

	@Test
	void rankOnlyRunReleasedByTheJanitorIsFailedAndFreesTheProfile() throws Exception {
		stored("A", Instant.now());
		startRank().andExpect(status().isAccepted());
		jdbc.update("update run set status = 'RUNNING', started_at = now(), lease_until = now() - interval '1 minute'");

		janitor.expireLostRuns();

		Run released = runs.findTop20ByProfileIdOrderByIdDesc(profileId).getFirst();
		assertEquals(RunStatus.FAILED, released.getStatus());
		assertEquals(RunMode.RANK_ONLY, released.getMode());
		assertTrue(released.getError().contains("lost"));
		startRank().andExpect(status().isAccepted());
	}

	@Test
	void fullRunStartsDoNotUseUpTheRankCap() throws Exception {
		stored("A", Instant.now());
		for (int i = 0; i < props.maxRunsPerOwnerPerDay(); i++) {
			jdbc.update("insert into client_run_start (client, owner_ref, created_at) values ('core-api', 'owner', now())");
		}

		mvc.perform(post("/v1/profiles/" + profileId + "/runs").header("Authorization", CORE_API)).andExpect(status().isTooManyRequests());
		startRank().andExpect(status().isAccepted());
	}

	@Test
	void modelRateLimitMidRunGivesAPartialRunAndTheRestStaysUnranked() throws Exception {
		for (int i = 0; i < 25; i++) {
			stored("Job " + i, Instant.now().minus(Duration.ofHours(1)));
		}
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		when(llm.complete(anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
			if (calls.incrementAndGet() > 1) {
				return new LlmClient.Completion(null, BigDecimal.ZERO, "The model provider is rate limiting the requests");
			}
			return answer(invocation.getArgument(1, String.class), 80);
		});

		startRank().andExpect(status().isAccepted());
		worker.runOnce();

		Run run = runs.findTop20ByProfileIdOrderByIdDesc(profileId).getFirst();
		assertEquals(RunStatus.PARTIAL, run.getStatus());
		assertEquals("The model provider is rate limiting the requests", run.getError());
		assertEquals(10, run.getRanked());
		mvc.perform(get("/v1/profiles/" + profileId + "/unranked").header("Authorization", CORE_API)).andExpect(jsonPath("$.count").value(15));
		verifyNoInteractions(fetcher);
	}

	@Test
	void unrankedCountFollowsTheAgeLimitAndTheCurrentPromptAndIsNotCapped() throws Exception {
		stored("Fresh", Instant.now().minus(Duration.ofHours(5)));
		stored("Too old", Instant.now().minus(Duration.ofDays(4)));
		for (int i = 0; i < 44; i++) {
			stored("More " + i, Instant.now().minus(Duration.ofHours(1)));
		}
		mvc.perform(get("/v1/profiles/" + profileId + "/unranked").header("Authorization", CORE_API)).andExpect(jsonPath("$.count").value(45));

		modelScores(60);
		startRank().andExpect(status().isAccepted());
		worker.runOnce();
		mvc.perform(get("/v1/profiles/" + profileId + "/unranked").header("Authorization", CORE_API)).andExpect(jsonPath("$.count").value(5));
		startRank().andExpect(status().isAccepted());
		worker.runOnce();
		mvc.perform(get("/v1/profiles/" + profileId + "/unranked").header("Authorization", CORE_API)).andExpect(jsonPath("$.count").value(0));

		profiles.update("core-api", profileId, new ProfileRequest("owner", "P", "Now only startups.", List.of("python developer"),
				List.of(new Region("FR", null)), List.of(SourceName.HELLOWORK), null));
		mvc.perform(get("/v1/profiles/" + profileId + "/unranked").header("Authorization", CORE_API)).andExpect(jsonPath("$.count").value(45));
	}

	@Test
	void sqlCountAgreesWithTheEntityCandidates() {
		var profile = profiles.owned("core-api", profileId);
		Job ranked = stored("Ranked", Instant.now());
		stored("Fresh", Instant.now().minus(Duration.ofHours(5)));
		stored("Too old", Instant.now().minus(Duration.ofDays(4)));
		Job undated = stored("Undated", null);
		Job givenUp = stored("Given up", Instant.now());
		Job failedOnce = stored("Failed once", Instant.now());
		Job outsideWindow = stored("Outside window", Instant.now());
		jdbc.update("update job set fetched_at = now() - interval '10 days' where id = ?", outsideWindow.getId());
		String hash = ranker.promptHash(profile);
		jdbc.update("insert into match (profile_id, job_id, score, reason, prompt_hash, ranked_at) values (?, ?, 50, 'r', ?, now())", profileId, ranked.getId(), hash);
		jdbc.update("insert into rank_failure (profile_id, job_id, prompt_hash, failures) values (?, ?, ?, 2)", profileId, givenUp.getId(), hash);
		jdbc.update("insert into rank_failure (profile_id, job_id, prompt_hash, failures) values (?, ?, ?, 1)", profileId, failedOnce.getId(), hash);
		assertAgree(profile, 3);   // Fresh, Undated, Failed once

		// a match or failure of another profile does not count; a match with an old hash does not hide the job
		long other = profiles.create("core-api", new ProfileRequest("owner-2", "Q", "Other.", List.of("python developer"),
				List.of(new Region("FR", null)), List.of(SourceName.HELLOWORK), null)).id();
		jdbc.update("insert into match (profile_id, job_id, score, reason, prompt_hash, ranked_at) values (?, ?, 50, 'r', ?, now())", other, undated.getId(), ranker.promptHash(profiles.owned("core-api", other)));
		jdbc.update("update match set prompt_hash = 'old' where profile_id = ?", profileId);
		assertAgree(profile, 4);   // Ranked is back

		var changed = profiles.owned("core-api", profileId);
		jdbc.update("update search_profile set expectations = 'Something else entirely.' where id = ?", profileId);
		assertAgree(profiles.owned("core-api", profileId), 5);   // new hash: the given-up job gets a fresh chance
		assertAgree(profiles.owned("core-api", other), 4);
	}

	private void assertAgree(com.jobsearch.joblist.profile.SearchProfile profile, int expected) {
		assertEquals(expected, rankCandidates.find(profile, Instant.now()).size());
		assertEquals(expected, rankCandidates.count(profile));
	}

	@Test
	void foreignOrUnknownProfileIsNotFound() throws Exception {
		mvc.perform(get("/v1/profiles/" + profileId + "/unranked").header("Authorization", BOT)).andExpect(status().isNotFound());
		mvc.perform(get("/v1/profiles/99999/unranked").header("Authorization", CORE_API)).andExpect(status().isNotFound());
		mvc.perform(post("/v1/profiles/" + profileId + "/runs?mode=rank").header("Authorization", BOT)).andExpect(status().isNotFound());
	}

	@Test
	void unknownModeIsRefusedAndFullIsTheDefault() throws Exception {
		mvc.perform(post("/v1/profiles/" + profileId + "/runs?mode=bogus").header("Authorization", CORE_API)).andExpect(status().isBadRequest());
		assertEquals(0, runs.count());
		mvc.perform(post("/v1/profiles/" + profileId + "/runs?mode=full").header("Authorization", CORE_API))
				.andExpect(status().isAccepted()).andExpect(jsonPath("$.mode").value("FULL"));
	}

	@Test
	void migrationAddsTheModeColumnsWithTheirDefaultsAndChecks() {
		assertEquals("FULL", jdbc.queryForObject("select column_default from information_schema.columns where table_name = 'run' and column_name = 'mode'", String.class)
				.replaceAll("[^A-Z_]", ""));
		jdbc.update("insert into run (profile_id, status, created_at) values (?, 'SUCCEEDED', now())", profileId);
		assertEquals("FULL", jdbc.queryForObject("select mode from run", String.class));
		org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
				() -> jdbc.update("insert into run (profile_id, status, mode, created_at) values (?, 'SUCCEEDED', 'OTHER', now())", profileId));
	}

	private org.springframework.test.web.servlet.ResultActions startRank() throws Exception {
		return mvc.perform(post("/v1/profiles/" + profileId + "/runs?mode=rank").header("Authorization", CORE_API));
	}

	private Job stored(String title, Instant postedAt) {
		Job job = TestData.job(SourceName.HELLOWORK, title);
		job.setFingerprint(job.getExternalId());
		job.setPostedAt(postedAt);
		job.setFetchedAt(Instant.now());
		return jobs.save(job);
	}

	private static LlmClient.Completion answer(String prompt, int score) {
		Matcher ids = JOB_TAG.matcher(prompt);
		List<String> results = ids.results().map(id -> "{\"id\": %s, \"score\": %d, \"reason\": \"Fits.\"}".formatted(id.group(1), score)).toList();
		return new LlmClient.Completion("{\"results\": [" + String.join(",", results) + "]}", new BigDecimal("0.001"), null);
	}

	private void modelScores(int score) {
		when(llm.complete(anyString(), anyString(), anyInt())).thenAnswer(invocation -> answer(invocation.getArgument(1, String.class), score));
	}
}
