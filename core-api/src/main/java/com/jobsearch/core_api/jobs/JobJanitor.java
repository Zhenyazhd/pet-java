package com.jobsearch.core_api.jobs;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Housekeeping for the job queue. Every step is a single idempotent statement, so running it on
 * several instances at once is safe.
 */
@Component
public class JobJanitor {

	private static final Logger log = LoggerFactory.getLogger(JobJanitor.class);
	/** A job still queued after this long fails — the user has stopped waiting. */
	private static final Duration MAX_QUEUE_AGE = Duration.ofMinutes(5);
	private static final Duration RETENTION = Duration.ofDays(7);
	private static final int DELETE_BATCH_SIZE = 500;

	private final JobRepository jobRepository;
	private final List<JobHandler> handlers;

	public JobJanitor(JobRepository jobRepository, List<JobHandler> handlers) {
		this.jobRepository = jobRepository;
		this.handlers = handlers;
	}

	@Scheduled(fixedDelay = 30_000)
	void run() {
		recoverExpiredLeases();
		failStaleQueuedJobs();
		deleteOldJobs();
	}

	/** A RUNNING job whose lease expired belongs to a dispatcher that died or hung. */
	void recoverExpiredLeases() {
		for (JobHandler handler : handlers) {
			String type = handler.type().name();
			int requeued = jobRepository.requeueExpiredLeases(type, handler.maxAttempts());
			int failed = jobRepository.failExpiredLeases(
					type, handler.maxAttempts(), JobDispatcher.UNAVAILABLE_CODE, JobDispatcher.UNAVAILABLE_MESSAGE);
			if (requeued > 0 || failed > 0) {
				log.warn("Job leases expired type={} requeued={} failed={}", type, requeued, failed);
			}
		}
	}

	void failStaleQueuedJobs() {
		Instant cutoff = Instant.now().minus(MAX_QUEUE_AGE);
		int failed = jobRepository.failQueuedBefore(cutoff, "queue_timeout", JobDispatcher.UNAVAILABLE_MESSAGE);
		if (failed > 0) {
			log.warn("Jobs timed out in queue count={}", failed);
		}
	}

	void deleteOldJobs() {
		Instant cutoff = Instant.now().minus(RETENTION);
		int deleted;
		do {
			deleted = jobRepository.deleteFinishedBefore(cutoff, DELETE_BATCH_SIZE);
		} while (deleted == DELETE_BATCH_SIZE);
	}
}
