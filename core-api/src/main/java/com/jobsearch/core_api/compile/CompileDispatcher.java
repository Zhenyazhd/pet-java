package com.jobsearch.core_api.compile;

import com.jobsearch.core_api.config.AppProperties;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Drains the compile queue: claims jobs, sends them to the latex-worker and records the outcome.
 * Runs in every core-api instance; claims use {@code SKIP LOCKED}, so instances never share a job.
 * The worker call happens outside any transaction so no pooled connection waits on it.
 */
@Component
public class CompileDispatcher {

	static final String UNAVAILABLE_MESSAGE = "The PDF could not be generated right now. Please try again later.";

	private static final Logger log = LoggerFactory.getLogger(CompileDispatcher.class);
	private static final Duration LEASE_SLACK = Duration.ofSeconds(30);
	private static final long POLL_INTERVAL_MILLIS = 500;

	private final CompileJobRepository jobRepository;
	private final ResumePdfCacheRepository cacheRepository;
	private final LatexWorkerClient workerClient;
	private final TransactionTemplate transactionTemplate;
	private final AppProperties.LatexWorker config;
	private final Semaphore slots;
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
	private volatile Instant pausedUntil = Instant.MIN;

	public CompileDispatcher(
			CompileJobRepository jobRepository,
			ResumePdfCacheRepository cacheRepository,
			LatexWorkerClient workerClient,
			PlatformTransactionManager transactionManager,
			AppProperties appProperties
	) {
		this.jobRepository = jobRepository;
		this.cacheRepository = cacheRepository;
		this.workerClient = workerClient;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.config = appProperties.getLatexWorker();
		this.slots = new Semaphore(config.getDispatchConcurrency());
	}

	/** Claims jobs while this instance has free slots; each claimed job is processed on its own virtual thread. */
	@Scheduled(fixedDelay = POLL_INTERVAL_MILLIS)
	void poll() {
		while (Instant.now().isAfter(pausedUntil) && slots.tryAcquire()) {
			Optional<CompileJob> claimed = jobRepository.claimNext(leaseSeconds());
			if (claimed.isEmpty()) {
				slots.release();
				return;
			}
			CompileJob job = claimed.get();
			executor.submit(() -> {
				try {
					process(job);
				}
				finally {
					slots.release();
				}
			});
		}
	}

	/** Sends one claimed (RUNNING) job to the worker and stores the result. */
	void process(CompileJob job) {
		int attempt = job.getAttempts();
		long waitMillis = Duration.between(job.getCreatedAt(), job.getStartedAt()).toMillis();
		log.info("Compile claimed jobId={} attempt={} waitMillis={}", job.getId(), attempt, waitMillis);

		long started = System.nanoTime();
		WorkerResult result = workerClient.compile(job.getSource());
		long workerMillis = (System.nanoTime() - started) / 1_000_000;

		int updated = switch (result) {
			case WorkerResult.Pdf(byte[] pdf) -> transactionTemplate.execute(status -> {
				cacheRepository.upsert(job.getSourceHash(), pdf);
				return jobRepository.markDone(job.getId(), attempt);
			});
			case WorkerResult.Rejected(String code, String message) ->
					// Only a LaTeX error helps the user fix their resume; other rejections are our bug.
					jobRepository.markFailed(job.getId(), attempt, code,
							"compile_error".equals(code) ? message : UNAVAILABLE_MESSAGE);
			case WorkerResult.Busy(long retryAfterSeconds) -> {
				pausedUntil = Instant.now().plusSeconds(retryAfterSeconds);
				log.info("Compile requeued jobId={} reason=worker_busy", job.getId());
				yield jobRepository.requeueWithoutCountingAttempt(job.getId(), attempt);
			}
			case WorkerResult.Unavailable(String reason) -> {
				if (attempt < config.getMaxAttempts()) {
					log.warn("Compile requeued jobId={} reason={}", job.getId(), reason);
					yield jobRepository.requeue(job.getId(), attempt);
				}
				log.error("Compile giving up jobId={} attempts={} reason={}", job.getId(), attempt, reason);
				yield jobRepository.markFailed(job.getId(), attempt, "worker_unavailable", UNAVAILABLE_MESSAGE);
			}
		};

		if (updated == 0) {
			log.warn("Compile result discarded jobId={} attempt={}: lease was already taken back", job.getId(), attempt);
			return;
		}
		long totalMillis = Duration.between(job.getCreatedAt(), Instant.now()).toMillis();
		log.info("Compile attempt done jobId={} result={} workerMillis={} totalMillis={}",
				job.getId(), result.getClass().getSimpleName(), workerMillis, totalMillis);
	}

	/** Lets in-flight jobs finish; anything still running afterwards is requeued by CompileJanitor once its lease expires. */
	@PreDestroy
	void shutdown() throws InterruptedException {
		executor.shutdown();
		executor.awaitTermination(30, TimeUnit.SECONDS);
	}

	private long leaseSeconds() {
		return config.getReadTimeoutSeconds() + LEASE_SLACK.toSeconds();
	}
}
