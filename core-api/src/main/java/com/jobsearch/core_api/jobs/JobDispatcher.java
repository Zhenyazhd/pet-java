package com.jobsearch.core_api.jobs;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the queue: claims jobs, runs them through their {@link JobHandler} and records the outcome.
 * Runs in every core-api instance; claims use {@code SKIP LOCKED}, so instances never share a job.
 * Each job type has its own slots, so slow types cannot starve fast ones.
 */
@Component
public class JobDispatcher {

	static final String UNAVAILABLE_CODE = "unavailable";
	static final String UNAVAILABLE_MESSAGE = "This could not be completed right now. Please try again later.";

	private static final Logger log = LoggerFactory.getLogger(JobDispatcher.class);
	private static final long POLL_INTERVAL_MILLIS = 500;

	private final JobRepository jobRepository;
	private final Map<JobType, Lane> lanes;
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	public JobDispatcher(JobRepository jobRepository, List<JobHandler> handlers) {
		this.jobRepository = jobRepository;
		this.lanes = handlers.stream().collect(Collectors.toMap(JobHandler::type, Lane::new));
	}

	/** Claims jobs while a type has free slots; each claimed job runs on its own virtual thread. */
	@Scheduled(fixedDelay = POLL_INTERVAL_MILLIS)
	void poll() {
		for (Lane lane : lanes.values()) {
			while (Instant.now().isAfter(lane.pausedUntil) && lane.slots.tryAcquire()) {
				Optional<Job> claimed = jobRepository.claimNext(lane.handler.type().name(), lane.handler.lease().toSeconds());
				if (claimed.isEmpty()) {
					lane.slots.release();
					break;
				}
				Job job = claimed.get();
				executor.submit(() -> {
					try {
						process(job);
					}
					finally {
						lane.slots.release();
					}
				});
			}
		}
	}

	/** Runs one claimed (RUNNING) job and records its outcome. */
	void process(Job job) {
		Lane lane = lanes.get(job.getType());
		int attempt = job.getAttempts();
		long waitMillis = Duration.between(job.getCreatedAt(), job.getStartedAt()).toMillis();
		log.info("Job claimed type={} jobId={} attempt={} waitMillis={}", job.getType(), job.getId(), attempt, waitMillis);

		long started = System.nanoTime();
		JobOutcome outcome = run(lane.handler, job);
		long runMillis = (System.nanoTime() - started) / 1_000_000;

		JobStatus next;
		int updated;
		switch (outcome) {
			case JobOutcome.Succeeded(String result) -> {
				next = JobStatus.DONE;
				updated = jobRepository.markDone(job.getId(), attempt, result);
			}
			case JobOutcome.Failed(String code, String message) -> {
				next = JobStatus.FAILED;
				updated = jobRepository.markFailed(job.getId(), attempt, code, message);
			}
			case JobOutcome.Busy(Duration retryAfter) -> {
				lane.pausedUntil = Instant.now().plus(retryAfter);
				next = JobStatus.QUEUED;
				updated = jobRepository.requeueWithoutCountingAttempt(job.getId(), attempt);
			}
			case JobOutcome.Retry(String reason) -> {
				if (attempt < lane.handler.maxAttempts()) {
					next = JobStatus.QUEUED;
					updated = jobRepository.requeue(job.getId(), attempt);
				}
				else {
					next = JobStatus.FAILED;
					updated = jobRepository.markFailed(job.getId(), attempt, UNAVAILABLE_CODE, UNAVAILABLE_MESSAGE);
				}
				log.warn("Job attempt failed type={} jobId={} attempt={} reason={}", job.getType(), job.getId(), attempt, reason);
			}
		}

		if (updated == 0) {
			log.warn("Job outcome discarded jobId={} attempt={}: lease was already taken back", job.getId(), attempt);
			return;
		}
		long totalMillis = Duration.between(job.getCreatedAt(), Instant.now()).toMillis();
		log.info("Job attempt done type={} jobId={} outcome={} next={} runMillis={} totalMillis={}",
				job.getType(), job.getId(), outcome.getClass().getSimpleName(), next, runMillis, totalMillis);
	}

	/** Lets in-flight jobs finish; anything still running afterwards is requeued by JobJanitor once its lease expires. */
	@PreDestroy
	void shutdown() throws InterruptedException {
		executor.shutdown();
		executor.awaitTermination(30, TimeUnit.SECONDS);
	}

	private static JobOutcome run(JobHandler handler, Job job) {
		try {
			return handler.run(job);
		}
		catch (RuntimeException ex) {
			// Handlers return their expected failures; this is a bug, but one worth another try.
			log.error("Job handler threw type={} jobId={}", job.getType(), job.getId(), ex);
			return new JobOutcome.Retry(ex.toString());
		}
	}

	/** Per-type slots and pause; the pause is set when the type's downstream service reports it is busy. */
	private static final class Lane {

		private final JobHandler handler;
		private final Semaphore slots;
		private volatile Instant pausedUntil = Instant.MIN;

		private Lane(JobHandler handler) {
			this.handler = handler;
			this.slots = new Semaphore(handler.concurrency());
		}
	}
}
