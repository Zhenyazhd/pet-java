package com.jobsearch.core_api.compile;

import com.jobsearch.core_api.config.AppProperties;
import com.jobsearch.core_api.jobs.Job;
import com.jobsearch.core_api.jobs.JobHandler;
import com.jobsearch.core_api.jobs.JobOutcome;
import com.jobsearch.core_api.jobs.JobType;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Compiles a queued resume (payload: Typst source) on the pdf-worker and caches the PDF. */
@Component
public class CompileJobHandler implements JobHandler {

	private static final String REJECTED_MESSAGE = "The PDF could not be generated. Please try again later.";

	private final PdfWorkerClient workerClient;
	private final ResumePdfCacheRepository cacheRepository;
	private final ResumeLayoutService layoutService;
	private final Duration lease;

	public CompileJobHandler(
			PdfWorkerClient workerClient,
			ResumePdfCacheRepository cacheRepository,
			ResumeLayoutService layoutService,
			AppProperties appProperties
	) {
		this.workerClient = workerClient;
		this.cacheRepository = cacheRepository;
		this.layoutService = layoutService;
		// The worker call may take the whole read timeout; the slack covers storing the result.
		this.lease = Duration.ofSeconds(appProperties.getPdfWorker().getReadTimeoutSeconds() + 30);
	}

	@Override
	public JobType type() {
		return JobType.RESUME_PDF;
	}

	@Override
	public int concurrency() {
		return 4;
	}

	@Override
	public int maxAttempts() {
		return 3;
	}

	@Override
	public Duration lease() {
		return lease;
	}

	@Override
	public JobOutcome run(Job job) {
		return switch (workerClient.compile(job.getPayload())) {
			case WorkerResult.Pdf(byte[] pdf, PageLayout layout) -> {
				cacheRepository.upsert(job.getDedupeKey(), pdf);
				if (layout != null) {
					// The job's dedupe key is the hash of the source, which is also the layout cache key.
					layoutService.remember(job.getDedupeKey(), layout);
				}
				yield new JobOutcome.Succeeded(null);
			}
			// Only a layout error helps the user fix their resume; other rejections are our bug.
			case WorkerResult.Rejected(String code, String message) ->
					new JobOutcome.Failed(code, "compile_error".equals(code) ? message : REJECTED_MESSAGE);
			case WorkerResult.Busy(long retryAfterSeconds) -> new JobOutcome.Busy(Duration.ofSeconds(retryAfterSeconds));
			case WorkerResult.Unavailable(String reason) -> new JobOutcome.Retry(reason);
		};
	}
}
