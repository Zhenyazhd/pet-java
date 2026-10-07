package com.jobsearch.core_api.vacancy;

import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.Hashes;
import com.jobsearch.core_api.jobs.JobDtos.JobResponse;
import com.jobsearch.core_api.jobs.JobService;
import com.jobsearch.core_api.jobs.JobType;
import com.jobsearch.core_api.vacancy.VacancyDtos.VacancyImportRequest;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Validates a vacancy import and queues it; {@link VacancyImportHandler} does the LLM parsing and saving. */
@Service
public class VacancyImportService {

	private final JobService jobService;
	private final VacancyRepository vacancyRepository;
	private final CurrentUserService currentUserService;
	private final ObjectMapper objectMapper;

	public VacancyImportService(
			JobService jobService,
			VacancyRepository vacancyRepository,
			CurrentUserService currentUserService,
			ObjectMapper objectMapper
	) {
		this.jobService = jobService;
		this.vacancyRepository = vacancyRepository;
		this.currentUserService = currentUserService;
		this.objectMapper = objectMapper;
	}

	public JobResponse enqueue(VacancyImportRequest request) {
		String url = request.url().strip();
		// Checked now so a duplicate fails the click, not the job a minute later.
		if (vacancyRepository.existsByUserIdAndUrl(currentUserService.requireUserId(), url)) {
			throw new ConflictException("Vacancy with this URL already exists");
		}
		String payload = objectMapper.writeValueAsString(new VacancyImportPayload(url, request.pastedText().strip()));
		// One import per URL at a time: the URL is what makes a vacancy unique.
		return jobService.enqueue(JobType.VACANCY_IMPORT, Hashes.sha256(url), payload);
	}
}
