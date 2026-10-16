package com.jobsearch.joblist.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobsearch.joblist.TestData;
import com.jobsearch.joblist.TestcontainersConfiguration;
import com.jobsearch.joblist.profile.ProfileDtos.ProfileRequest;
import com.jobsearch.joblist.profile.SearchProfileService;
import com.jobsearch.joblist.source.Region;
import com.jobsearch.joblist.source.SourceName;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** The ledger on a real database: the checks that decide whether a paid call may start. Budget 5.00, cap 0.30 per source. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SpendLedgerTest {

	private static final Duration DAY = Duration.ofHours(24);

	@Autowired
	private SpendLedger ledger;
	@Autowired
	private Janitor janitor;
	@Autowired
	private RunRepository runs;
	@Autowired
	private RunPipeline pipeline;
	@Autowired
	private SearchProfileService profiles;
	@Autowired
	private JdbcTemplate jdbc;

	private long profileId;
	private long runId;

	@BeforeEach
	void setUp() {
		TestData.reset(jdbc);
		profileId = profiles.create("core-api", new ProfileRequest("owner", "P", "Anything.", List.of("python"),
				List.of(new Region("FR", null)), List.of(SourceName.HELLOWORK), null)).id();
		runId = runningRun();
	}

	private long runningRun() {
		Run run = runs.save(Run.queued(profileId));
		run.start(Instant.now().plusSeconds(600));
		return runs.save(run).getId();
	}

	@Test
	void parallelReservationsCannotAllPassTheSameBudgetCheck() throws Exception {
		jdbc.update("insert into spend_ledger (kind, source, query_key, status, cost_usd, created_at) values ('APIFY', 'WTTJ', 'seed', 'SUCCEEDED', 4.40, now())");
		int threads = 12;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			String key = "query-" + i;
			results.add(pool.submit(() -> {
				start.await();
				return ledger.reserveApify(runId, profileId, SourceName.WTTJ, key, DAY).granted();
			}));
		}
		start.countDown();
		long granted = 0;
		for (Future<Boolean> result : results) {
			granted += result.get() ? 1 : 0;
		}
		pool.shutdown();

		// 4.40 + 0.30 + 0.30 = 5.00 fits; a third reservation would pass the budget
		assertEquals(2, granted);
		assertEquals(0, new BigDecimal("5.00").compareTo(ledger.spentThisMonth()));
	}

	@Test
	void parallelModelCallReservationsCannotAllPassTheSameBudgetCheck() throws Exception {
		jdbc.update("insert into spend_ledger (kind, status, cost_usd, created_at) values ('LLM', 'SUCCEEDED', 1.97, now())");
		int threads = 12;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			results.add(pool.submit(() -> {
				start.await();
				return ledger.reserveLlmCall(runId, profileId).granted();
			}));
		}
		start.countDown();
		long granted = 0;
		for (Future<Boolean> result : results) {
			granted += result.get() ? 1 : 0;
		}
		pool.shutdown();

		// 1.97 + 3 x 0.01 = 2.00 fits (budget 2.00); a fourth would pass it
		assertEquals(3, granted);
		assertEquals(0, new BigDecimal("2.00").compareTo(ledger.llmSpentThisMonth()));
	}

	@Test
	void modelAndApifyBudgetsAreSeparate() {
		jdbc.update("insert into spend_ledger (kind, status, cost_usd, created_at) values ('LLM', 'SUCCEEDED', 1.995, now())");

		assertFalse(ledger.llmHasRoom());
		assertTrue(ledger.reserveApify(runId, profileId, SourceName.WTTJ, "q", DAY).granted(), "the model budget does not stop Apify reservations");
	}

	@Test
	void reservationWithoutAnEndStaysCountedAndBlocksTheSameSearch() {
		SpendLedger.Reservation reserved = ledger.reserveApify(runId, profileId, SourceName.WTTJ, "q", DAY);
		assertTrue(reserved.granted());

		// the process dies here: nothing settles the reservation; the janitor releases the run
		janitor.releaseInterruptedRuns();

		assertEquals(RunStatus.FAILED, runs.findById(runId).orElseThrow().getStatus());
		assertEquals("LOST", jdbc.queryForObject("select status from spend_ledger", String.class));
		assertEquals(0, new BigDecimal("0.30").compareTo(ledger.spentThisMonth()), "the worst case stays counted");
		long next = runningRun();
		assertEquals("fetched less than 24 hours ago", ledger.reserveApify(next, profileId, SourceName.WTTJ, "q", DAY).refusal());
	}

	@Test
	void settlingReplacesTheReservationWithTheRealCost() {
		SpendLedger.Reservation reserved = ledger.reserveApify(runId, profileId, SourceName.WTTJ, "q", DAY);

		ledger.settle(reserved.id(), true, new BigDecimal("0.12"), "apify-1");

		assertEquals(0, new BigDecimal("0.12").compareTo(ledger.spentThisMonth()));
		assertTrue(ledger.lastSuccess(SourceName.WTTJ, "q") != null);
		assertEquals(null, ledger.lastSuccess(SourceName.WTTJ, "another search"));
	}

	@Test
	void aCallApifyRefusedToStartDoesNotBlockTheRetry() {
		SpendLedger.Reservation reserved = ledger.reserveApify(runId, profileId, SourceName.WTTJ, "q", DAY);
		ledger.settle(reserved.id(), false, BigDecimal.ZERO, null);

		assertTrue(ledger.reserveApify(runId, profileId, SourceName.WTTJ, "q", DAY).granted());
	}

	@Test
	void aFailedCallThatCostMoneyDoesBlockTheRetry() {
		SpendLedger.Reservation reserved = ledger.reserveApify(runId, profileId, SourceName.WTTJ, "q", DAY);
		ledger.settle(reserved.id(), false, new BigDecimal("0.05"), "apify-1");

		assertFalse(ledger.reserveApify(runId, profileId, SourceName.WTTJ, "q", DAY).granted());
	}

	@Test
	void theSameSearchOnAnotherSourceIsAnotherCall() {
		assertTrue(ledger.reserveApify(runId, profileId, SourceName.WTTJ, "q", DAY).granted());

		assertTrue(ledger.reserveApify(runId, profileId, SourceName.HELLOWORK, "q", DAY).granted());
	}

	@Test
	void aRunTheJanitorReleasedCannotReserve() {
		runs.releaseIfRunning(runId, Instant.now(), "lost", false);

		SpendLedger.Reservation reservation = ledger.reserveApify(runId, profileId, SourceName.WTTJ, "q", DAY);

		assertTrue(reservation.runReleased());
		assertEquals(0, jdbc.queryForObject("select count(*) from spend_ledger", Integer.class));
	}

	@Test
	void lateWorkerCannotWriteOverARelease() {
		runs.releaseIfRunning(runId, Instant.now(), "lost", false);

		int renewed = runs.renewLease(runId, Instant.now().plusSeconds(600));
		int finished = runs.finishIfRunning(runId, RunStatus.SUCCEEDED, Instant.now(), 1, 1, 1, BigDecimal.ONE, null);

		assertEquals(0, renewed);
		assertEquals(0, finished);
		assertEquals(RunStatus.FAILED, runs.findById(runId).orElseThrow().getStatus());
	}

	@Test
	void janitorLeavesARunWhoseLeaseWasRenewed() {
		runs.renewLease(runId, Instant.now().plusSeconds(3600));

		janitor.expireLostRuns();

		assertEquals(RunStatus.RUNNING, runs.findById(runId).orElseThrow().getStatus());
		// and the guard itself: a release that only applies to an expired lease does nothing to a live one
		assertEquals(0, runs.releaseIfRunning(runId, Instant.now(), "lost", true));
	}

	@Test
	void pipelineStopsAtOnceWhenItsRunWasReleased() {
		Run released = runs.findById(runId).orElseThrow();
		runs.releaseIfRunning(runId, Instant.now(), "lost", false);

		pipeline.execute(released);

		assertEquals(0, jdbc.queryForObject("select count(*) from spend_ledger", Integer.class), "nothing was reserved or spent");
		assertEquals("lost", runs.findById(runId).orElseThrow().getError(), "the janitor's verdict stands");
	}

	@Test
	void spendIsKeptWhenTheRunsAreDeleted() {
		SpendLedger.Reservation reserved = ledger.reserveApify(runId, profileId, SourceName.WTTJ, "q", DAY);
		ledger.settle(reserved.id(), true, new BigDecimal("0.20"), "apify-1");

		jdbc.update("delete from search_profile");

		assertEquals(0, jdbc.queryForObject("select count(*) from run", Integer.class));
		assertEquals(0, new BigDecimal("0.20").compareTo(ledger.spentThisMonth()));
	}
}
