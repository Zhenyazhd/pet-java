package com.jobsearch.joblist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.jobsearch.joblist.job.Job;
import com.jobsearch.joblist.profile.ProfileDtos.ProfileRequest;
import com.jobsearch.joblist.profile.ProfileDtos.ProfileResponse;
import com.jobsearch.joblist.profile.SearchProfileService;
import com.jobsearch.joblist.rank.LlmClient;
import com.jobsearch.joblist.rank.MatchRepository;
import com.jobsearch.joblist.run.Run;
import com.jobsearch.joblist.run.RunRepository;
import com.jobsearch.joblist.run.RunService;
import com.jobsearch.joblist.run.RunSource;
import com.jobsearch.joblist.run.RunSourceRepository;
import com.jobsearch.joblist.run.RunStatus;
import com.jobsearch.joblist.run.RunWorker;
import com.jobsearch.joblist.run.SourceStatus;
import com.jobsearch.joblist.run.UsageService;
import com.jobsearch.joblist.source.ApifyFetcher;
import com.jobsearch.joblist.source.Region;
import com.jobsearch.joblist.source.SourceName;
import com.jobsearch.joblist.source.SourceResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

/** A run from the queue to the stored matches, with the Apify fetcher and the model replaced by fakes. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PipelineTest {

	private static final Pattern JOB_TAG = Pattern.compile("<job id=\"(\\d+)\">");

	@Autowired
	private SearchProfileService profiles;
	@Autowired
	private RunService runService;
	@Autowired
	private RunWorker worker;
	@Autowired
	private RunRepository runs;
	@Autowired
	private RunSourceRepository runSources;
	@Autowired
	private MatchRepository matches;
	@Autowired
	private UsageService usage;
	@Autowired
	private com.jobsearch.joblist.run.Janitor janitor;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ObjectMapper json;
	@MockitoBean
	private ApifyFetcher fetcher;
	@MockitoBean
	private LlmClient llm;

	private ProfileResponse profile;

	@BeforeEach
	void setUp() {
		TestData.reset(jdbc);
		profile = profiles.create("core-api", request("Backend", "Looks for Python backend work in Paris."));
	}

	@Test
	void queuedRunFetchesStoresRanksAndRecordsTheCost() {
		fetchReturns(List.of(TestData.job(SourceName.HELLOWORK, "Senior Python Developer"), TestData.job(SourceName.HELLOWORK, "Chef de cuisine")), "0.25");
		modelScores(80);

		Run run = runOnce();

		assertEquals(RunStatus.SUCCEEDED, run.getStatus());
		assertEquals(2, run.getFetched());
		assertEquals(2, run.getNewJobs());
		assertEquals(2, run.getRanked(), "nothing is filtered out before the ranking");
		assertEquals(0, new BigDecimal("0.251").compareTo(run.getCostUsd()));
		assertEquals(2, matches.count());
		assertEquals(0, new BigDecimal("0.25").compareTo(usage.current().spentUsd()));
	}

	@Test
	void secondRunSkipsTheSourceAndDoesNotRankAgain() {
		fetchReturns(List.of(TestData.job(SourceName.HELLOWORK, "Python Developer")), "0.25");
		modelScores(80);
		runOnce();

		Run second = runOnce();

		assertEquals(RunStatus.SUCCEEDED, second.getStatus());
		assertEquals(0, second.getRanked());
		RunSource skipped = runSources.findByRunIdIn(List.of(second.getId())).getFirst();
		assertEquals(SourceStatus.SKIPPED, skipped.getStatus());
		assertTrue(skipped.getError().contains("hours ago"), skipped.getError());
	}

	@Test
	void changedExpectationsRankTheStoredJobsAgainAndKeepDeliveredAt() {
		fetchReturns(List.of(TestData.job(SourceName.HELLOWORK, "Python Developer")), "0.25");
		modelScores(60);
		runOnce();
		var match = matches.findAll().getFirst();
		match.markDelivered();
		matches.save(match);
		profiles.update("core-api", profile.id(), request("Backend", "Now wants only Python jobs in a startup."));
		modelScores(90);

		Run second = runOnce();

		assertEquals(1, second.getRanked());
		var reranked = matches.findAll().getFirst();
		assertEquals(90, reranked.getScore());
		assertTrue(reranked.getDeliveredAt() != null, "re-ranking must not make a delivered match new again");
	}

	@Test
	void failingSourceFailsTheRunButKeepsTheRecordedCost() {
		when(fetcher.fetch(eq(SourceName.HELLOWORK), anyString(), any(), anyInt(), any()))
				.thenReturn(SourceResult.failed("apify-1", new BigDecimal("0.05"), "Actor run ended as FAILED"));

		Run run = runOnce();

		assertEquals(RunStatus.FAILED, run.getStatus());
		assertEquals(0, new BigDecimal("0.05").compareTo(run.getCostUsd()));
		assertEquals(0, matches.count());
	}

	@Test
	void monthlyBudgetStopsTheSourceBeforeItIsCalled() {
		jdbc.update("insert into spend_ledger (kind, source, query_key, status, cost_usd, created_at) values ('APIFY', 'WTTJ', 'other', 'SUCCEEDED', 4.90, now())");

		Run run = runOnce();

		RunSource skipped = runSources.findByRunIdIn(List.of(run.getId())).getFirst();
		assertEquals(SourceStatus.SKIPPED, skipped.getStatus());
		assertEquals("monthly budget", skipped.getError());
	}

	@Test
	void jobWhoseAnswerIsNeverUsableIsSkippedAndTheOthersAreStillRanked() {
		Job poison = TestData.job(SourceName.HELLOWORK, "Python Developer");
		poison.setDescriptionText("POISON makes the model break the format.");
		fetchReturns(List.of(TestData.job(SourceName.HELLOWORK, "Python Developer"), poison, TestData.job(SourceName.HELLOWORK, "Python Developer")), "0.25");
		// the model answers every job except the one whose text makes it lose the format
		when(llm.complete(anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
			List<String> results = java.util.Arrays.stream(invocation.getArgument(1, String.class).split("(?=<job id=\")"))
					.filter(block -> block.startsWith("<job id=") && !block.contains("POISON"))
					.map(block -> "{\"id\": %s, \"score\": 80, \"reason\": \"Fits.\"}".formatted(JOB_TAG.matcher(block).results().findFirst().orElseThrow().group(1)))
					.toList();
			return new LlmClient.Completion("{\"results\": [" + String.join(",", results) + "]}", new BigDecimal("0.001"), null);
		});

		Run run = runOnce();

		assertEquals(RunStatus.PARTIAL, run.getStatus(), "the job the model never answered is reported, not hidden");
		assertTrue(run.getError().contains("1 jobs could not be ranked"), run.getError());
		assertEquals(2, run.getRanked());
		assertEquals(2, matches.count());
	}

	@Test
	void modelThatNeverFollowsTheFormatLeavesTheJobsUnrankedAndMakesTheRunPartial() {
		fetchReturns(java.util.stream.Stream.generate(() -> TestData.job(SourceName.HELLOWORK, "Python Developer")).limit(10).toList(), "0.25");
		when(llm.complete(anyString(), anyString(), anyInt())).thenReturn(new LlmClient.Completion("{\"results\": []}", new BigDecimal("0.001"), null));

		Run run = runOnce();

		assertEquals(RunStatus.PARTIAL, run.getStatus());
		assertTrue(run.getError().contains("keeps giving answers that cannot be used"), run.getError());
		// nothing usable: the batch and then each half are asked, which shows that the model is not working now
		org.mockito.Mockito.verify(llm, org.mockito.Mockito.times(3)).complete(anyString(), anyString(), anyInt());
		assertEquals(0, matches.count());
	}

	@Test
	void failedCallToTheModelMakesTheRunPartialAndKeepsTheJobsForNextTime() {
		fetchReturns(List.of(TestData.job(SourceName.HELLOWORK, "Python Developer")), "0.25");
		when(llm.complete(anyString(), anyString(), anyInt())).thenReturn(new LlmClient.Completion(null, BigDecimal.ZERO, "The model could not be reached"));

		Run run = runOnce();

		assertEquals(RunStatus.PARTIAL, run.getStatus());
		assertEquals(0, matches.count());
		assertFalse(run.getError().isBlank());
	}

	@Test
	void sourceSwitchedOffAfterTheProfileWasSavedIsNeverCalled() {
		jdbc.update("update search_profile set sources = '{LINKEDIN,HELLOWORK}'");
		fetchReturns(List.of(TestData.job(SourceName.HELLOWORK, "Python Developer")), "0.25");
		modelScores(80);

		Run run = runOnce();

		org.mockito.Mockito.verify(fetcher, org.mockito.Mockito.never()).fetch(eq(SourceName.LINKEDIN), anyString(), any(), anyInt(), any());
		RunSource linkedin = runSources.findByRunIdIn(List.of(run.getId())).stream()
				.filter(source -> source.getSource() == SourceName.LINKEDIN).findFirst().orElseThrow();
		assertEquals("source disabled", linkedin.getError());
		assertEquals(RunStatus.SUCCEEDED, run.getStatus());
	}

	@Test
	void deletingTheProfileDoesNotEraseWhatWasSpentNorTheWaitBetweenFetches() {
		fetchReturns(List.of(TestData.job(SourceName.HELLOWORK, "Python Developer")), "0.25");
		modelScores(80);
		runOnce();
		assertEquals(0, new BigDecimal("0.25").compareTo(usage.current().spentUsd()));

		profiles.delete("core-api", profile.id());
		profile = profiles.create("core-api", request("Backend again", "The same search, saved again."));
		Run again = runOnce();

		assertEquals(0, new BigDecimal("0.25").compareTo(usage.current().spentUsd()), "the money spent stays counted");
		org.mockito.Mockito.verify(fetcher, org.mockito.Mockito.times(1)).fetch(eq(SourceName.HELLOWORK), anyString(), any(), anyInt(), any());
		assertEquals(SourceStatus.SKIPPED, runSources.findByRunIdIn(List.of(again.getId())).getFirst().getStatus());
	}

	@Test
	void anotherProfileWithTheSameQueryDoesNotPayForItAgain() {
		fetchReturns(List.of(TestData.job(SourceName.HELLOWORK, "Python Developer")), "0.25");
		modelScores(80);
		runOnce();
		ProfileResponse other = profiles.create("core-api", new ProfileRequest("owner-2", "Same search", "Someone else.",
				List.of("Python  Developer"), List.of(new Region("FR", null)), List.of(SourceName.HELLOWORK), null));

		runService.start("core-api", other.id());
		assertTrue(worker.runOnce());

		org.mockito.Mockito.verify(fetcher, org.mockito.Mockito.times(1)).fetch(eq(SourceName.HELLOWORK), anyString(), any(), anyInt(), any());
		assertEquals(0, new BigDecimal("0.25").compareTo(usage.current().spentUsd()));
	}

	@Test
	void answerWithOneBadEntryKeepsTheGoodOnesAndOnlyTheMissingJobIsAskedAgain() {
		fetchReturns(java.util.stream.Stream.generate(() -> TestData.job(SourceName.HELLOWORK, "Python Developer")).limit(3).toList(), "0.25");
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		java.util.List<String> prompts = new java.util.concurrent.CopyOnWriteArrayList<>();
		when(llm.complete(anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
			prompts.add(invocation.getArgument(1, String.class));
			List<String> ids = JOB_TAG.matcher(invocation.getArgument(1, String.class)).results().map(id -> id.group(1)).toList();
			boolean first = calls.incrementAndGet() == 1;
			List<String> results = new java.util.ArrayList<>();
			for (int i = 0; i < ids.size(); i++) {
				int score = first && i == 0 ? 180 : 80;   // the first answer has one entry out of range
				results.add("{\"id\": %s, \"score\": %d, \"reason\": \"Fits.\"}".formatted(ids.get(i), score));
			}
			return new LlmClient.Completion("{\"results\": [" + String.join(",", results) + "]}", new BigDecimal("0.001"), null);
		});

		Run run = runOnce();

		assertEquals(RunStatus.SUCCEEDED, run.getStatus());
		assertEquals(3, run.getRanked());
		assertEquals(2, calls.get(), "one call for the batch, one for the single job that was missing");
		assertEquals(1, JOB_TAG.matcher(prompts.get(1)).results().count(), "the second call asks for the missing job only");
		assertEquals(3, matches.count());
	}

	@Test
	void modelThatNeverFollowsTheFormatStopsThePassAfterTheFirstBatchAndItsHalves() {
		fetchReturns(java.util.stream.Stream.generate(() -> TestData.job(SourceName.HELLOWORK, "Python Developer")).limit(25).toList(), "0.25");
		when(llm.complete(anyString(), anyString(), anyInt())).thenReturn(new LlmClient.Completion("no json at all", new BigDecimal("0.001"), null));

		runOnce();

		// 25 jobs are 3 batches, but the pass stops after the first batch and its two halves
		org.mockito.Mockito.verify(llm, org.mockito.Mockito.times(3)).complete(anyString(), anyString(), anyInt());
		assertEquals(0, matches.count());
	}

	@Test
	void everyModelCallIsLimitedInLength() {
		fetchReturns(List.of(TestData.job(SourceName.HELLOWORK, "Python Developer")), "0.25");
		modelScores(80);

		runOnce();

		org.mockito.ArgumentCaptor<Integer> maxTokens = org.mockito.ArgumentCaptor.forClass(Integer.class);
		org.mockito.Mockito.verify(llm).complete(anyString(), anyString(), maxTokens.capture());
		assertTrue(maxTokens.getValue() > 0 && maxTokens.getValue() <= 2000, "max_tokens " + maxTokens.getValue());
	}

	@Test
	void modelCallsAreRecordedInTheLedgerAndStopAtTheMonthlyModelBudget() {
		fetchReturns(java.util.stream.Stream.generate(() -> TestData.job(SourceName.HELLOWORK, "Python Developer")).limit(30).toList(), "0.25");
		modelScores(80);
		// 2.00 budget, 0.01 reserved per call: 1.99 + 0.01 fits once; after that call settles at 0.001 the next one does not
		jdbc.update("insert into spend_ledger (kind, status, cost_usd, created_at) values ('LLM', 'SUCCEEDED', 1.99, now())");

		Run run = runOnce();

		assertEquals(RunStatus.PARTIAL, run.getStatus());
		assertTrue(run.getError().contains("model budget"), run.getError());
		assertEquals(10, run.getRanked(), "the first batch was ranked, then the budget was used up");
		assertEquals(0, new BigDecimal("1.991").compareTo(usage.current().llmSpentUsd()));
	}

	private void startsToday(String owner, int count) {
		for (int i = 0; i < count; i++) {
			jdbc.update("insert into client_run_start (client, owner_ref, created_at) values ('core-api', ?, now())", owner);
		}
	}

	@Test
	void anOwnerCannotStartMoreRunsInADayThanTheLimit() {
		startsToday("owner-1", 4);

		org.junit.jupiter.api.Assertions.assertThrows(com.jobsearch.joblist.common.TooManyRequestsException.class,
				() -> runService.start("core-api", profile.id()));
	}

	@Test
	void theLimitSurvivesDeletingAndCreatingProfilesUnderAnotherName() {
		startsToday("owner-1", 4);
		profiles.delete("core-api", profile.id());
		ProfileResponse again = profiles.create("core-api", request("Fresh", "A new profile of the same owner."));

		org.junit.jupiter.api.Assertions.assertThrows(com.jobsearch.joblist.common.TooManyRequestsException.class,
				() -> runService.start("core-api", again.id()));
	}

	@Test
	void aClientCannotStartMoreRunsInADayThanItsOwnLimitWhateverTheOwnerNames() {
		for (int i = 0; i < 6; i++) {
			startsToday("owner-" + (10 + i), 1);
		}

		org.junit.jupiter.api.Assertions.assertThrows(com.jobsearch.joblist.common.TooManyRequestsException.class,
				() -> runService.start("core-api", profile.id()));
	}

	@Test
	void startingARunThatIsAlreadyWaitingDoesNotCountAgainstTheLimit() {
		startsToday("owner-1", 3);
		var first = runService.start("core-api", profile.id());

		var again = runService.start("core-api", profile.id());

		assertEquals(first.id(), again.id());
	}

	@Test
	void callWhoseCostTheProviderDidNotReportStaysAtTheReservedWorstCase() {
		fetchReturns(List.of(TestData.job(SourceName.HELLOWORK, "Python Developer")), "0.25");
		when(llm.complete(anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
			Matcher ids = JOB_TAG.matcher(invocation.getArgument(1, String.class));
			List<String> results = ids.results().map(id -> "{\"id\": %s, \"score\": 80, \"reason\": \"Fits.\"}".formatted(id.group(1))).toList();
			return new LlmClient.Completion("{\"results\": [" + String.join(",", results) + "]}", BigDecimal.ZERO, false, null, false);
		});

		runOnce();

		assertEquals("LOST", jdbc.queryForObject("select status from spend_ledger where kind = 'LLM'", String.class));
		assertEquals(0, new BigDecimal("0.01").compareTo(usage.current().llmSpentUsd()), "counted at the reservation, not at zero");
	}

	@Test
	void cutOffAnswerIsSplitInHalvesWhichRankTheBatchAndNobodyIsBlamed() {
		fetchReturns(java.util.stream.Stream.generate(() -> TestData.job(SourceName.HELLOWORK, "Python Developer")).limit(15).toList(), "0.25");
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		when(llm.complete(anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
			if (calls.incrementAndGet() == 1) {
				return new LlmClient.Completion(null, new BigDecimal("0.003"), true, null, true);
			}
			Matcher ids = JOB_TAG.matcher(invocation.getArgument(1, String.class));
			List<String> results = ids.results().map(id -> "{\"id\": %s, \"score\": 80, \"reason\": \"Fits.\"}".formatted(id.group(1))).toList();
			return new LlmClient.Completion("{\"results\": [" + String.join(",", results) + "]}", new BigDecimal("0.001"), null);
		});

		Run run = runOnce();

		assertEquals(RunStatus.SUCCEEDED, run.getStatus());
		assertEquals(15, run.getRanked(), "the cut-off batch was ranked in two halves, then the second batch");
		assertEquals(4, calls.get());
		assertEquals(0, jdbc.queryForObject("select count(*) from rank_failure", Integer.class), "no job was blamed");
	}

	@Test
	void aModelThatFailsForTwoRunsAndThenWorksStillRanksEverything() {
		fetchReturns(java.util.stream.Stream.generate(() -> TestData.job(SourceName.HELLOWORK, "Python Developer")).limit(10).toList(), "0.25");
		when(llm.complete(anyString(), anyString(), anyInt())).thenReturn(new LlmClient.Completion(null, new BigDecimal("0.003"), true, null, true));
		runOnce();
		runOnce();
		assertEquals(0, matches.count());
		assertEquals(0, jdbc.queryForObject("select count(*) from rank_failure", Integer.class), "an outage of the model is not a failure of the jobs");

		modelScores(80);
		Run third = runOnce();

		assertEquals(10, third.getRanked());
		assertEquals(RunStatus.SUCCEEDED, third.getStatus());
	}

	@Test
	void aJobAloneWhoseAnswerIsUnusableIsNotBlamedWhileTheModelAnswersNothingAtAll() {
		fetchReturns(List.of(TestData.job(SourceName.HELLOWORK, "Python Developer")), "0.25");
		when(llm.complete(anyString(), anyString(), anyInt())).thenReturn(new LlmClient.Completion("no json", new BigDecimal("0.001"), null));

		runOnce();
		runOnce();
		runOnce();

		assertEquals(0, jdbc.queryForObject("select count(*) from rank_failure", Integer.class), "when nothing is ever ranked it is not known whose fault it is");
		org.mockito.Mockito.verify(llm, org.mockito.Mockito.times(3)).complete(anyString(), anyString(), anyInt());
	}

	@Test
	void changedPromptGivesAJobThatWasGivenUpAFreshChance() {
		fetchReturns(List.of(TestData.job(SourceName.HELLOWORK, "Python Developer"), poisonJob()), "0.25");
		poisonDroppedBy();
		runOnce();
		runOnce();
		assertEquals(1, matches.count());

		profiles.update("core-api", profile.id(), request("Backend", "Different expectations now."));
		modelScores(70);

		assertEquals(2, runOnce().getRanked(), "both jobs, the given-up one included, are ranked again for the new prompt");
	}

	/** The model answers every job except the ones whose text contains POISON. */
	private void poisonDroppedBy() {
		when(llm.complete(anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
			List<String> results = java.util.Arrays.stream(invocation.getArgument(1, String.class).split("(?=<job id=\")"))
					.filter(block -> block.startsWith("<job id=") && !block.contains("POISON"))
					.map(block -> "{\"id\": %s, \"score\": 80, \"reason\": \"Fits.\"}".formatted(JOB_TAG.matcher(block).results().findFirst().orElseThrow().group(1)))
					.toList();
			return new LlmClient.Completion("{\"results\": [" + String.join(",", results) + "]}", new BigDecimal("0.001"), null);
		});
	}

	private Job poisonJob() {
		Job poison = TestData.job(SourceName.HELLOWORK, "Python Developer");
		poison.setDescriptionText("POISON makes the model drop this job.");
		return poison;
	}

	@Test
	void twoDroppedJobsInOneBatchAreBlamedOnTheirOwnAndTheModelIsNotReportedDown() {
		List<Job> jobs = new java.util.ArrayList<>(java.util.stream.Stream.generate(() -> TestData.job(SourceName.HELLOWORK, "Python Developer")).limit(8).toList());
		jobs.add(poisonJob());
		jobs.add(poisonJob());
		fetchReturns(jobs, "0.25");
		poisonDroppedBy();

		Run run = runOnce();

		assertEquals(8, run.getRanked());
		assertEquals(RunStatus.PARTIAL, run.getStatus());
		assertTrue(run.getError().contains("2 jobs could not be ranked"), run.getError());
		org.mockito.Mockito.verify(llm, org.mockito.Mockito.times(2)).complete(anyString(), anyString(), anyInt());
		assertEquals(2, jdbc.queryForObject("select count(*) from rank_failure where failures = 1", Integer.class));
	}

	@Test
	void twoDroppedJobsInsideAHalfAreBlamedToo() {
		List<Job> jobs = new java.util.ArrayList<>(List.of(poisonJob(), poisonJob()));
		java.util.stream.Stream.generate(() -> TestData.job(SourceName.HELLOWORK, "Python Developer")).limit(8).forEach(jobs::add);
		fetchReturns(jobs, "0.25");
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		when(llm.complete(anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
			if (calls.incrementAndGet() == 1) {
				return new LlmClient.Completion(null, new BigDecimal("0.003"), true, null, true);   // the whole batch is cut off
			}
			List<String> results = java.util.Arrays.stream(invocation.getArgument(1, String.class).split("(?=<job id=\")")
					).filter(block -> block.startsWith("<job id=") && !block.contains("POISON"))
					.map(block -> "{\"id\": %s, \"score\": 80, \"reason\": \"Fits.\"}".formatted(JOB_TAG.matcher(block).results().findFirst().orElseThrow().group(1)))
					.toList();
			return new LlmClient.Completion("{\"results\": [" + String.join(",", results) + "]}", new BigDecimal("0.001"), null);
		});

		Run run = runOnce();

		assertEquals(8, run.getRanked());
		// the batch, the first half (3 usable), the two it left out, the second half
		assertEquals(4, calls.get());
		assertEquals(2, jdbc.queryForObject("select count(*) from rank_failure", Integer.class), "the two dropped jobs are blamed, once");
		assertEquals(RunStatus.PARTIAL, run.getStatus());
		assertFalse(run.getError().contains("keeps giving"), run.getError());
	}

	@Test
	void jobThatFailedTwiceOnItsOwnIsNotOfferedAgainForThisPrompt() {
		List<Job> jobs = new java.util.ArrayList<>(java.util.stream.Stream.generate(() -> TestData.job(SourceName.HELLOWORK, "Python Developer")).limit(3).toList());
		jobs.add(poisonJob());
		fetchReturns(jobs, "0.25");
		poisonDroppedBy();
		runOnce();
		assertEquals(1, jdbc.queryForObject("select failures from rank_failure", Integer.class), "blamed once: the model answered the other three");
		int callsAfterFirst = org.mockito.Mockito.mockingDetails(llm).getInvocations().size();
		runOnce();
		assertEquals(callsAfterFirst + 1, org.mockito.Mockito.mockingDetails(llm).getInvocations().size(), "blamed once is still offered (alone, one call)");
		jdbc.update("update rank_failure set failures = 2");
		int callsAfterSecond = org.mockito.Mockito.mockingDetails(llm).getInvocations().size();

		runOnce();

		assertEquals(callsAfterSecond, org.mockito.Mockito.mockingDetails(llm).getInvocations().size(), "failed twice: not asked about again for this prompt");
	}

	@Test
	void aLoneJobAsAWholeBatchIsAskedAgainOnEveryRunAndNeverBlamedWithoutEvidence() {
		fetchReturns(List.of(TestData.job(SourceName.HELLOWORK, "Python Developer"), poisonJob()), "0.25");
		poisonDroppedBy();
		runOnce();   // the first job is ranked, the poison job is dropped when asked again alone: blamed once (the model answered)
		runOnce();   // now the poison job is the only candidate: one call, not blamed
		runOnce();

		assertEquals(1, matches.count());
		assertEquals(1, jdbc.queryForObject("select max(failures) from rank_failure", Integer.class), "no second failure without evidence");
	}

	@Test
	void anOutageAfterAGoodBatchBlamesNobodyForTheLoneJobThatFollows() {
		fetchReturns(java.util.stream.Stream.generate(() -> TestData.job(SourceName.HELLOWORK, "Python Developer")).limit(11).toList(), "0.25");
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		when(llm.complete(anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
			if (calls.incrementAndGet() > 1) {
				return new LlmClient.Completion("sorry", new BigDecimal("0.001"), null);
			}
			Matcher ids = JOB_TAG.matcher(invocation.getArgument(1, String.class));
			List<String> results = ids.results().map(id -> "{\"id\": %s, \"score\": 80, \"reason\": \"Fits.\"}".formatted(id.group(1))).toList();
			return new LlmClient.Completion("{\"results\": [" + String.join(",", results) + "]}", new BigDecimal("0.001"), null);
		});

		Run run = runOnce();

		assertEquals(10, run.getRanked());
		assertEquals(0, jdbc.queryForObject("select count(*) from rank_failure", Integer.class), "the tail job failed because the model went down");
	}

	@Test
	void aJobBlamedOnceIsNotGivenUpByAnOutageAndIsRankedWhenTheModelRecovers() {
		fetchReturns(List.of(TestData.job(SourceName.HELLOWORK, "Python Developer"), poisonJob()), "0.25");
		poisonDroppedBy();
		runOnce();   // the poison job is blamed once
		when(llm.complete(anyString(), anyString(), anyInt())).thenReturn(new LlmClient.Completion("<html>502 Bad Gateway</html>", new BigDecimal("0.001"), null));
		runOnce();   // an outage: the lone job fails alone, with no evidence

		modelScores(80);
		Run recovered = runOnce();

		assertEquals(1, recovered.getRanked(), "the job that was only blamed once is still offered and ranked");
	}

	@Test
	void garbageOnABatchOfTwoBlamesNobody() {
		fetchReturns(List.of(TestData.job(SourceName.HELLOWORK, "Python Developer"), TestData.job(SourceName.HELLOWORK, "Chef")), "0.25");
		when(llm.complete(anyString(), anyString(), anyInt())).thenReturn(new LlmClient.Completion("sorry", new BigDecimal("0.001"), null));

		Run run = runOnce();

		assertEquals(RunStatus.PARTIAL, run.getStatus());
		assertTrue(run.getError().contains("keeps giving answers"), run.getError());
		assertEquals(0, jdbc.queryForObject("select count(*) from rank_failure", Integer.class), "the model is down, the jobs are not at fault");
	}

	@Test
	void withTheModelBudgetUsedUpTheSourcesAreNotFetched() {
		jdbc.update("insert into spend_ledger (kind, status, cost_usd, created_at) values ('LLM', 'SUCCEEDED', 1.995, now())");
		fetchReturns(List.of(TestData.job(SourceName.HELLOWORK, "Python Developer")), "0.25");

		Run run = runOnce();

		org.mockito.Mockito.verifyNoInteractions(fetcher);
		assertEquals("model budget", runSources.findByRunIdIn(List.of(run.getId())).getFirst().getError());
		assertEquals(0, new BigDecimal("0").compareTo(usage.current().spentUsd()), "no Apify money for jobs that cannot be ranked");
	}

	@Test
	void aClientCannotHoldMoreProfilesThanItsLimit() {
		for (int i = 0; i < 49; i++) {
			profiles.create("core-api", new ProfileRequest("owner-x" + i, "P" + i, "Anything.", List.of("python developer"),
					List.of(new Region("FR", null)), List.of(SourceName.HELLOWORK), null));
		}

		org.junit.jupiter.api.Assertions.assertThrows(com.jobsearch.joblist.common.BadRequestException.class,
				() -> profiles.create("core-api", new ProfileRequest("owner-last", "Last", "Anything.", List.of("python developer"),
						List.of(new Region("FR", null)), List.of(SourceName.HELLOWORK), null)));
	}

	/** A profile that goes through HelloWork and then WTTJ, so there is a "next source" to stop before. */
	private ProfileResponse twoSourceProfile() {
		return profiles.create("core-api", new ProfileRequest("owner-3", "Two sources", "Python work.",
				List.of("python developer"), List.of(new Region("FR", null)), List.of(SourceName.HELLOWORK, SourceName.WTTJ), null));
	}

	private Run runOf(ProfileResponse target) {
		runService.start("core-api", target.id());
		assertTrue(worker.runOnce());
		return runs.findTop20ByProfileIdOrderByIdDesc(target.id()).getFirst();
	}

	@Test
	void runReleasedWhileAFetchWasGoingStopsBeforeTheNextSource() {
		ProfileResponse two = twoSourceProfile();
		when(fetcher.fetch(eq(SourceName.HELLOWORK), anyString(), any(), anyInt(), any())).thenAnswer(invocation -> {
			jdbc.update("update run set status = 'FAILED', error = 'released by the janitor' where status = 'RUNNING'");
			return new SourceResult(List.of(TestData.job(SourceName.HELLOWORK, "Python Developer")), 1, 0, new BigDecimal("0.10"), "apify-1", null);
		});

		Run run = runOf(two);

		org.mockito.Mockito.verify(fetcher, org.mockito.Mockito.never()).fetch(eq(SourceName.WTTJ), anyString(), any(), anyInt(), any());
		Run stored = runs.findById(run.getId()).orElseThrow();
		assertEquals(RunStatus.FAILED, stored.getStatus());
		assertEquals("released by the janitor", stored.getError(), "the worker did not write over the release");
		assertEquals(0, jdbc.queryForObject("select count(*) from spend_ledger where source = 'WTTJ'", Integer.class));
	}

	@Test
	void realReleaseDuringAFetchKeepsTheJobsAndLeavesTheSpendLostAtTheCap() {
		when(fetcher.fetch(eq(SourceName.HELLOWORK), anyString(), any(), anyInt(), any())).thenAnswer(invocation -> {
			// the lease runs out and the real janitor releases the run while the fetch is still going
			jdbc.update("update run set lease_until = now() - interval '1 minute' where status = 'RUNNING'");
			janitor.expireLostRuns();
			return new SourceResult(List.of(TestData.job(SourceName.HELLOWORK, "Python Developer")), 1, 0, new BigDecimal("0.10"), "apify-1", null);
		});

		Run run = runOnce();

		assertEquals(RunStatus.FAILED, runs.findById(run.getId()).orElseThrow().getStatus());
		assertTrue(runs.findById(run.getId()).orElseThrow().getError().contains("lost"));
		assertEquals("LOST", jdbc.queryForObject("select status from spend_ledger", String.class));
		assertEquals(0, new BigDecimal("0.30").compareTo(usage.current().spentUsd()), "the late worker's settle did not lower the cost");
		assertEquals(1, jdbc.queryForObject("select count(*) from job", Integer.class), "what was paid for is kept");
	}

	@Test
	void runReleasedWhileRankingStopsBeforeTheNextModelCall() {
		fetchReturns(java.util.stream.Stream.generate(() -> TestData.job(SourceName.HELLOWORK, "Python Developer")).limit(25).toList(), "0.25");
		when(llm.complete(anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
			jdbc.update("update run set status = 'FAILED', error = 'released by the janitor' where status = 'RUNNING'");
			Matcher ids = JOB_TAG.matcher(invocation.getArgument(1, String.class));
			List<String> results = ids.results().map(id -> "{\"id\": %s, \"score\": 80, \"reason\": \"Fits.\"}".formatted(id.group(1))).toList();
			return new LlmClient.Completion("{\"results\": [" + String.join(",", results) + "]}", new BigDecimal("0.001"), null);
		});

		Run run = runOnce();

		org.mockito.Mockito.verify(llm, org.mockito.Mockito.times(1)).complete(anyString(), anyString(), anyInt());
		assertEquals("released by the janitor", runs.findById(run.getId()).orElseThrow().getError());
	}

	@Test
	void leaseIsRenewedBeforeEachStepWhileTheRunProgresses() {
		ProfileResponse two = twoSourceProfile();
		java.util.concurrent.atomic.AtomicReference<Instant> leaseSeenByTheSecondSource = new java.util.concurrent.atomic.AtomicReference<>();
		when(fetcher.fetch(eq(SourceName.HELLOWORK), anyString(), any(), anyInt(), any())).thenAnswer(invocation -> {
			// the first step is over soon: shorten the lease so that only a renewal can lengthen it again
			jdbc.update("update run set lease_until = now() + interval '1 second' where status = 'RUNNING'");
			return new SourceResult(List.of(), 0, 0, new BigDecimal("0.10"), "apify-1", null);
		});
		when(fetcher.fetch(eq(SourceName.WTTJ), anyString(), any(), anyInt(), any())).thenAnswer(invocation -> {
			leaseSeenByTheSecondSource.set(jdbc.queryForObject("select lease_until from run where status = 'RUNNING'", java.sql.Timestamp.class).toInstant());
			return new SourceResult(List.of(), 0, 0, new BigDecimal("0.10"), "apify-2", null);
		});

		runOf(two);

		assertTrue(leaseSeenByTheSecondSource.get().isAfter(Instant.now().plusSeconds(300)), "the lease was extended before the second source");
	}

	@Test
	void sourceThatCrashesIsCountedAtTheCapBecauseItsCostIsUnknown() {
		when(fetcher.fetch(eq(SourceName.HELLOWORK), anyString(), any(), anyInt(), any())).thenThrow(new IllegalStateException("boom"));

		Run run = runOnce();

		assertEquals(RunStatus.FAILED, run.getStatus());
		assertEquals(0, new BigDecimal("0.30").compareTo(run.getCostUsd()));
		assertEquals(0, new BigDecimal("0.30").compareTo(usage.current().spentUsd()));
	}

	@Test
	void failureToStoreTheJobsFailsTheSourceAndDoesNotAdvanceTheLookbackWindow() {
		fetchReturns(List.of(TestData.job(SourceName.HELLOWORK, "Python Developer")), "0.25");
		jdbc.execute("create or replace function fail_job_insert() returns trigger as $$ begin raise exception 'disk full'; end $$ language plpgsql");
		jdbc.execute("create trigger fail_job_insert before insert on job for each row execute function fail_job_insert()");
		try {
			Run run = runOnce();

			RunSource source = runSources.findByRunIdIn(List.of(run.getId())).getFirst();
			assertEquals(SourceStatus.FAILED, source.getStatus());
			assertEquals(0, new BigDecimal("0.25").compareTo(usage.current().spentUsd()), "the money was spent and stays counted");
			assertEquals("FAILED", jdbc.queryForObject("select status from spend_ledger", String.class), "no success on the ledger for jobs that were not saved");
		}
		finally {
			jdbc.execute("drop trigger fail_job_insert on job");
			jdbc.execute("drop function fail_job_insert()");
		}
	}

	@Test
	void failureToSettleLeavesTheReservationCountedAndTheJanitorClosesItAsLost() {
		fetchReturns(List.of(TestData.job(SourceName.HELLOWORK, "Python Developer")), "0.12");
		modelScores(80);
		jdbc.execute("create or replace function fail_ledger_update() returns trigger as $$ begin raise exception 'blip'; end $$ language plpgsql");
		jdbc.execute("create trigger fail_ledger_update before update on spend_ledger for each row execute function fail_ledger_update()");
		Run run;
		try {
			run = runOnce();
		}
		finally {
			jdbc.execute("drop trigger fail_ledger_update on spend_ledger");
			jdbc.execute("drop function fail_ledger_update()");
		}

		assertEquals(1, run.getNewJobs(), "the paid result was kept");
		assertEquals("RESERVED", jdbc.queryForObject("select status from spend_ledger where kind = 'APIFY'", String.class));
		assertEquals(0, new BigDecimal("0.30").compareTo(usage.current().spentUsd()));

		janitor.expireLostRuns();

		assertEquals("LOST", jdbc.queryForObject("select status from spend_ledger where kind = 'APIFY'", String.class));
		assertEquals(0, jdbc.queryForObject("select count(*) from spend_ledger where status = 'RESERVED'", Integer.class), "the model calls' reservations are closed too");
		assertEquals(0, new BigDecimal("0.30").compareTo(usage.current().lostUsd()));
		assertEquals(0, BigDecimal.ZERO.compareTo(usage.current().reservedUsd()));
	}

	private Run runOnce() {
		runService.start("core-api", profile.id());
		assertTrue(worker.runOnce());
		return runs.findTop20ByProfileIdOrderByIdDesc(profile.id()).getFirst();
	}

	private void fetchReturns(List<Job> jobs, String cost) {
		when(fetcher.fetch(eq(SourceName.HELLOWORK), anyString(), any(), anyInt(), any()))
				.thenReturn(new SourceResult(jobs, jobs.size(), 0, new BigDecimal(cost), "apify-1", null));
	}

	/** Answers every batch with the given score for each job id found in the prompt. */
	private void modelScores(int score) {
		when(llm.complete(anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
			Matcher ids = JOB_TAG.matcher(invocation.getArgument(1, String.class));
			List<String> results = ids.results()
					.map(id -> "{\"id\": %s, \"score\": %d, \"reason\": \"Fits.\"}".formatted(id.group(1), score)).toList();
			return new LlmClient.Completion("{\"results\": [" + String.join(",", results) + "]}", new BigDecimal("0.001"), null);
		});
	}

	private static ProfileRequest request(String name, String expectations) {
		return new ProfileRequest("owner-1", name, expectations, List.of("python developer"),
				List.of(new Region("FR", null)), List.of(SourceName.HELLOWORK), null);
	}
}
