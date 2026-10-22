package com.jobsearch.joblist.run;

import com.jobsearch.joblist.config.JobListProperties;
import com.jobsearch.joblist.job.JobRepository;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Housekeeping: frees runs whose worker died, and deletes data past its retention. */
@Component
public class Janitor {

	private static final Logger log = LoggerFactory.getLogger(Janitor.class);
	private static final String LOST = "The run was lost (the service stopped while it ran); the cost of the source in progress is counted at the cap";
	/** The ledger is kept for the budget (this month) and the waits between fetches (a few days); this is far past both. */
	private static final Duration LEDGER_RETENTION = Duration.ofDays(70);

	private final RunRepository runs;
	private final JobRepository jobs;
	private final SpendLedger ledger;
	private final RunStarts starts;
	private final JobListProperties properties;

	public Janitor(RunRepository runs, JobRepository jobs, SpendLedger ledger, RunStarts starts, JobListProperties properties) {
		this.runs = runs;
		this.jobs = jobs;
		this.ledger = ledger;
		this.starts = starts;
		this.properties = properties;
	}

	/**
	 * When the service starts, every run still marked RUNNING belonged to the process that stopped. That holds because
	 * there is one replica and the Deployment replaces it (strategy Recreate); with several replicas this would fail the
	 * runs of the others, and only {@link #expireLostRuns()} should be left.
	 */
	@PostConstruct
	public void releaseInterruptedRuns() {
		for (Run run : runs.findByStatus(RunStatus.RUNNING)) {
			release(run, false);
		}
	}

	@Scheduled(fixedDelay = 300_000)
	public void expireLostRuns() {
		for (Run run : runs.findByStatusAndLeaseUntilBefore(RunStatus.RUNNING, Instant.now())) {
			release(run, true);
		}
		int closed = ledger.closeOrphanedReservations();
		if (closed > 0) {
			log.warn("Reservations nobody settled were closed as lost count={}", closed);
		}
	}

	/** Guarded: a worker that finished or renewed its lease in the meantime keeps its run. */
	private void release(Run run, boolean onlyExpired) {
		if (runs.releaseIfRunning(run.getId(), Instant.now(), LOST, onlyExpired) > 0) {
			ledger.markLost(run.getId());
			log.warn("Lost run released runId={}", run.getId());
		}
	}

	@Scheduled(cron = "0 0 3 * * *", zone = "UTC")
	public void deleteOldData() {
		Instant now = Instant.now();
		int oldJobs = jobs.deleteFetchedBefore(now.minus(Duration.ofDays(properties.retentionDays())));
		int oldRuns = runs.deleteCreatedBefore(now.minus(Duration.ofDays(properties.runRetentionDays())));
		int oldSpend = ledger.deleteBefore(now.minus(LEDGER_RETENTION));
		int oldStarts = starts.deleteBefore(now.minus(Duration.ofDays(3)));
		log.info("Retention done jobs={} runs={} spendRows={} runStarts={}", oldJobs, oldRuns, oldSpend, oldStarts);
	}
}
