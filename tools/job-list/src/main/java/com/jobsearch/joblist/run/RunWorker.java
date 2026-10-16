package com.jobsearch.joblist.run;

import com.jobsearch.joblist.config.JobListProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Takes queued runs one at a time and executes them. */
@Component
public class RunWorker {

	private static final Logger log = LoggerFactory.getLogger(RunWorker.class);

	private final RunRepository runs;
	private final RunPipeline pipeline;
	private final TransactionTemplate transaction;
	private final Duration lease;

	public RunWorker(RunRepository runs, RunPipeline pipeline, PlatformTransactionManager transactionManager, JobListProperties properties) {
		this.runs = runs;
		this.pipeline = pipeline;
		this.transaction = new TransactionTemplate(transactionManager);
		// Only until the first step renews it (see RunPipeline.stepLease); the janitor frees a run whose lease runs out.
		this.lease = pipeline.stepLease();
	}

	/** The delay is read from {@code job-list.worker-poll-interval}. */
	@Scheduled(fixedDelayString = "${job-list.worker-poll-interval}")
	public void poll() {
		runOnce();
	}

	/** Executes one queued run if there is one; returns whether it did. */
	public boolean runOnce() {
		Optional<Run> claimed = claim();
		claimed.ifPresent(run -> {
			log.info("Run started runId={} profileId={}", run.getId(), run.getProfileId());
			pipeline.execute(run);
			log.info("Run finished runId={} status={} fetched={} new={} ranked={} costUsd={}", run.getId(), run.getStatus(),
					run.getFetched(), run.getNewJobs(), run.getRanked(), run.getCostUsd());
		});
		return claimed.isPresent();
	}

	/** The row lock is held only while the run is marked RUNNING; the work itself happens outside a transaction. */
	private Optional<Run> claim() {
		return transaction.execute(status -> runs.claimNext().map(run -> {
			run.start(Instant.now().plus(lease));
			return runs.save(run);
		}));
	}
}
