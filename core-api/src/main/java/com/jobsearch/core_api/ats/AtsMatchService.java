package com.jobsearch.core_api.ats;

import com.jobsearch.core_api.common.BadRequestException;
import com.jobsearch.core_api.ats.AtsDtos.MatchRequest;
import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.common.Hashes;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.common.ServiceUnavailableException;
import com.jobsearch.core_api.compile.BuiltPdfService;
import com.jobsearch.core_api.config.AppProperties;
import com.jobsearch.core_api.jobs.JobDtos.JobResponse;
import com.jobsearch.core_api.jobs.JobService;
import com.jobsearch.core_api.jobs.JobType;
import com.jobsearch.core_api.vacancy.VacancyRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Validates a vacancy match request and queues it; {@link AtsMatchHandler} does the scoring. */
@Service
public class AtsMatchService {

	private static final int MAX_VACANCY_CHARS = 20_000;

	private final JobService jobService;
	private final BuiltPdfService builtPdfService;
	private final VacancyRepository vacancyRepository;
	private final CurrentUserService currentUserService;
	private final ObjectMapper objectMapper;
	private final boolean enabled;

	public AtsMatchService(
			JobService jobService,
			BuiltPdfService builtPdfService,
			VacancyRepository vacancyRepository,
			CurrentUserService currentUserService,
			ObjectMapper objectMapper,
			AppProperties appProperties
	) {
		this.jobService = jobService;
		this.builtPdfService = builtPdfService;
		this.vacancyRepository = vacancyRepository;
		this.currentUserService = currentUserService;
		this.objectMapper = objectMapper;
		this.enabled = appProperties.getAtsWorker().isEnabled();
	}

	public JobResponse enqueue(MatchRequest request) {
		if (!enabled) {
			// A fixed answer with no Retry-After: waiting will not help. 503 is not 404, which means "no such job".
			throw new ServiceUnavailableException("The vacancy match is not available yet.", null);
		}
		String vacancyText = request.vacancyContext().strip();
		if (vacancyText.length() > MAX_VACANCY_CHARS) {
			throw new BadRequestException("vacancyContext exceeds " + MAX_VACANCY_CHARS + " characters");
		}
		// The model scores the text an ATS reads from the PDF, so the PDF must be the current resume's.
		String sourceHash = builtPdfService.require(request.compileJobId()).sourceHash();
		// Checked now so a wrong id fails the click, not the job minutes later.
		Long vacancyId = request.vacancyId();
		if (vacancyId != null && !vacancyRepository.existsByIdAndUserId(vacancyId, currentUserService.requireUserId())) {
			throw new NotFoundException("Vacancy not found: " + vacancyId);
		}

		String payload = objectMapper.writeValueAsString(new AtsMatchPayload(sourceHash, vacancyText, vacancyId));
		String dedupeKey = Hashes.sha256(sourceHash, vacancyText, Objects.toString(vacancyId, ""));
		return jobService.enqueue(JobType.ATS_MATCH, dedupeKey, payload);
	}
}
