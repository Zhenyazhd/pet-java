package com.jobsearch.core_api.compile;

import com.jobsearch.core_api.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Housekeeping for the compile queue. Every step is a single idempotent statement, so running it on
 * several instances at once is safe.
 */
@Component
public class CompileJanitor {

	private static final Logger log = LoggerFactory.getLogger(CompileJanitor.class);
	private static final Duration JOB_RETENTION = Duration.ofDays(7);
	private static final Duration UNUSED_PDF_RETENTION = Duration.ofDays(30);
	private static final int DELETE_BATCH_SIZE = 500;

	private final CompileJobRepository jobRepository;
	private final ResumePdfCacheRepository cacheRepository;
	private final AppProperties.LatexWorker config;

	public CompileJanitor(
			CompileJobRepository jobRepository,
			ResumePdfCacheRepository cacheRepository,
			AppProperties appProperties
	) {
		this.jobRepository = jobRepository;
		this.cacheRepository = cacheRepository;
		this.config = appProperties.getLatexWorker();
	}

	@Scheduled(fixedDelay = 30_000)
	void run() {
		recoverExpiredLeases();
		failStaleQueuedJobs();
		deleteOldRows();
	}

	/** A RUNNING job whose lease expired belongs to a dispatcher that died or hung. */
	void recoverExpiredLeases() {
		int requeued = jobRepository.requeueExpiredLeases(config.getMaxAttempts());
		int failed = jobRepository.failExpiredLeases(
				config.getMaxAttempts(), "worker_unavailable", CompileDispatcher.UNAVAILABLE_MESSAGE);
		if (requeued > 0 || failed > 0) {
			log.warn("Compile leases expired requeued={} failed={}", requeued, failed);
		}
	}

	void failStaleQueuedJobs() {
		Instant cutoff = Instant.now().minusSeconds(config.getQueueMaxAgeSeconds());
		int failed = jobRepository.failQueuedBefore(cutoff, "queue_timeout", CompileDispatcher.UNAVAILABLE_MESSAGE);
		if (failed > 0) {
			log.warn("Compile jobs timed out in queue count={}", failed);
		}
	}

	void deleteOldRows() {
		Instant jobCutoff = Instant.now().minus(JOB_RETENTION);
		int deleted;
		do {
			deleted = jobRepository.deleteFinishedBefore(jobCutoff, DELETE_BATCH_SIZE);
		} while (deleted == DELETE_BATCH_SIZE);

		Instant pdfCutoff = Instant.now().minus(UNUSED_PDF_RETENTION);
		do {
			deleted = cacheRepository.deleteUnusedBefore(pdfCutoff, DELETE_BATCH_SIZE);
		} while (deleted == DELETE_BATCH_SIZE);
	}
}
