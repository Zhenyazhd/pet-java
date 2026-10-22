package com.jobsearch.core_api.ats;

import com.jobsearch.core_api.ats.AtsDtos.MatchRequest;
import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.common.Hashes;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.common.ServiceUnavailableException;
import com.jobsearch.core_api.common.TooManyRequestsException;
import com.jobsearch.core_api.compile.BuiltPdfService;
import com.jobsearch.core_api.config.AppProperties;
import com.jobsearch.core_api.jobs.JobDtos.JobResponse;
import com.jobsearch.core_api.jobs.JobRepository;
import com.jobsearch.core_api.jobs.JobService;
import com.jobsearch.core_api.jobs.JobType;
import com.jobsearch.core_api.vacancy.VacancyRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Validates a vacancy match request and queues it; {@link AtsMatchHandler} does the scoring. */
@Service
public class AtsMatchService {

	private final JobService jobService;
	private final JobRepository jobRepository;
	private final BuiltPdfService builtPdfService;
	private final VacancyRepository vacancyRepository;
	private final CurrentUserService currentUserService;
	private final ObjectMapper objectMapper;
	private final boolean enabled;
	private final int maxPerDay;

	public AtsMatchService(
			JobService jobService,
			JobRepository jobRepository,
			BuiltPdfService builtPdfService,
			VacancyRepository vacancyRepository,
			CurrentUserService currentUserService,
			ObjectMapper objectMapper,
			AppProperties appProperties
	) {
		this.jobService = jobService;
		this.jobRepository = jobRepository;
		this.builtPdfService = builtPdfService;
		this.vacancyRepository = vacancyRepository;
		this.currentUserService = currentUserService;
		this.objectMapper = objectMapper;
		this.enabled = appProperties.getAtsWorker().isEnabled();
		this.maxPerDay = appProperties.getAtsWorker().getMaxMatchesPerUserPerDay();
	}

	public JobResponse get(UUID jobId) {
		return jobService.get(jobId, JobType.ATS_MATCH);
	}

	public JobResponse enqueue(MatchRequest request) {
		if (!enabled) {
			// A fixed answer with no Retry-After: waiting will not help. 503 is not 404, which means "no such job".
			throw new ServiceUnavailableException("The vacancy match is not available yet.", null);
		}
		String vacancyText = request.vacancyContext().strip();
		// The model scores the text an ATS reads from the PDF, so the PDF must be the current resume's.
		String sourceHash = builtPdfService.require(request.compileJobId()).sourceHash();
		// Checked now so a wrong id fails the click, not the job minutes later.
		Long vacancyId = request.vacancyId();
		if (vacancyId != null && !vacancyRepository.existsByIdAndUserId(vacancyId, currentUserService.requireUserId())) {
			throw new NotFoundException("Vacancy not found: " + vacancyId);
		}

		// A failed or repeated match is a new paid model call (only running ones are deduplicated), so cap them per day.
		if (maxPerDay > 0 && jobRepository.countByUserIdAndTypeAndCreatedAtAfter(
				currentUserService.requireUserId(), JobType.ATS_MATCH, Instant.now().minus(Duration.ofDays(1))) >= maxPerDay) {
			throw new TooManyRequestsException("Daily limit of vacancy matches reached. Try again tomorrow.", null);
		}

		String payload = objectMapper.writeValueAsString(new AtsMatchPayload(sourceHash, vacancyText, vacancyId));
		String dedupeKey = Hashes.sha256(sourceHash, vacancyText, Objects.toString(vacancyId, ""));
		return jobService.enqueue(JobType.ATS_MATCH, dedupeKey, payload);
	}
}
