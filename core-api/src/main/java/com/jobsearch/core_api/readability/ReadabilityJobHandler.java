package com.jobsearch.core_api.readability;

import com.jobsearch.core_api.compile.ResumePdfCache;
import com.jobsearch.core_api.compile.ResumePdfCacheRepository;
import com.jobsearch.core_api.config.AppProperties;
import com.jobsearch.core_api.jobs.Job;
import com.jobsearch.core_api.jobs.JobHandler;
import com.jobsearch.core_api.jobs.JobOutcome;
import com.jobsearch.core_api.jobs.JobType;
import java.time.Duration;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Sends a cached resume PDF to the ats-worker and stores its report as the job result. */
@Component
public class ReadabilityJobHandler implements JobHandler {

	private static final Logger log = LoggerFactory.getLogger(ReadabilityJobHandler.class);
	private static final String REJECTED_MESSAGE = "The PDF could not be checked. Please try again later.";
	/** Worker codes that describe the PDF itself, so the user can act on them. */
	private static final Set<String> USER_FACING_CODES =
			Set.of("not_a_pdf", "unreadable_pdf", "encrypted", "too_many_pages", "too_much_text", "page_too_large");

	private final AtsWorkerClient workerClient;
	private final ResumePdfCacheRepository cacheRepository;
	private final ObjectMapper objectMapper;
	private final Duration lease;
	private final boolean enabled;

	public ReadabilityJobHandler(
			AtsWorkerClient workerClient,
			ResumePdfCacheRepository cacheRepository,
			ObjectMapper objectMapper,
			AppProperties appProperties
	) {
		this.workerClient = workerClient;
		this.cacheRepository = cacheRepository;
		this.objectMapper = objectMapper;
		// The worker call may take the whole read timeout; the slack covers storing the result.
		this.lease = Duration.ofSeconds(appProperties.getAtsWorker().getReadTimeoutSeconds() + 30);
		this.enabled = appProperties.getAtsWorker().isEnabled();
	}

	@Override
	public boolean disabled() {
		return !enabled;
	}

	@Override
	public JobType type() {
		return JobType.PDF_READABILITY;
	}

	@Override
	public int concurrency() {
		return 2;
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
		ReadabilityPayload payload;
		try {
			payload = objectMapper.readValue(job.getPayload(), ReadabilityPayload.class);
		}
		catch (JacksonException ex) {
			log.error("Readability payload unreadable jobId={}", job.getId(), ex);
			return new JobOutcome.Failed("bad_payload", "This request could not be processed.");
		}
		byte[] pdf = cacheRepository.findById(payload.sourceHash()).map(ResumePdfCache::getPdf).orElse(null);
		if (pdf == null) {
			// Cached PDFs unused for 30 days are deleted by PdfCacheJanitor.
			return new JobOutcome.Failed("pdf_expired", "The PDF has expired. Build the PDF again.");
		}
		return switch (workerClient.check(pdf, payload.expected())) {
			case AtsWorkerResult.Report(var report) -> {
				log.info("Readability checked jobId={} verdict={} score={}", job.getId(),
						report.path("verdict").asString(""), report.path("score").asInt(-1));
				yield new JobOutcome.Succeeded(objectMapper.writeValueAsString(report));
			}
			case AtsWorkerResult.Rejected(String code, String message) ->
					new JobOutcome.Failed(code, USER_FACING_CODES.contains(code) && !message.isBlank() ? message : REJECTED_MESSAGE);
			case AtsWorkerResult.Busy(long retryAfterSeconds) -> new JobOutcome.Busy(Duration.ofSeconds(retryAfterSeconds));
			case AtsWorkerResult.Unavailable(String reason) -> new JobOutcome.Retry(reason);
		};
	}
}
