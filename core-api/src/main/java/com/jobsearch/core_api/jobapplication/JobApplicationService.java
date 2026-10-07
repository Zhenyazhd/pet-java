package com.jobsearch.core_api.jobapplication;

import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.common.Strings;
import com.jobsearch.core_api.jobapplication.JobApplicationDtos.JobApplicationRequest;
import com.jobsearch.core_api.jobapplication.JobApplicationDtos.JobApplicationResponse;
import com.jobsearch.core_api.jobapplication.JobApplicationDtos.JobApplicationUpdateRequest;
import com.jobsearch.core_api.vacancy.Vacancy;
import com.jobsearch.core_api.vacancy.VacancyRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tracks application status per vacancy (scoped to vacancy owner). */
@Service
@Transactional
public class JobApplicationService {

	private static final Logger log = LoggerFactory.getLogger(JobApplicationService.class);

	private final JobApplicationRepository jobApplicationRepository;
	private final VacancyRepository vacancyRepository;
	private final CurrentUserService currentUserService;

	public JobApplicationService(
			JobApplicationRepository jobApplicationRepository,
			VacancyRepository vacancyRepository,
			CurrentUserService currentUserService
	) {
		this.jobApplicationRepository = jobApplicationRepository;
		this.vacancyRepository = vacancyRepository;
		this.currentUserService = currentUserService;
	}

	/**
	 * Upsert by vacancy: create if missing, otherwise update status/notes on the existing row.
	 * Vacancies from import/manual create already have a NOT_APPLIED application.
	 */
	public JobApplicationResponse create(JobApplicationRequest request) {
		long userId = currentUserService.requireUserId();
		Vacancy vacancy = vacancyRepository.findByIdAndUserId(request.vacancyId(), userId)
				.orElseThrow(() -> new NotFoundException("Vacancy not found: " + request.vacancyId()));

		JobApplication application = vacancy.getApplication();
		boolean created = application == null;
		if (created) {
			application = new JobApplication();
			vacancy.setApplication(application);
		}
		applyStatus(application, request.status());
		if (request.notes() != null) {
			application.setNotes(Strings.blankToNull(request.notes()));
		}

		JobApplication saved = jobApplicationRepository.save(application);
		log.info(
				"{} application id={} vacancyId={} userId={} status={}",
				created ? "Created" : "Updated existing",
				saved.getId(),
				request.vacancyId(),
				userId,
				saved.getStatus()
		);
		return toResponse(saved);
	}

	public JobApplicationResponse update(Long id, JobApplicationUpdateRequest request) {
		JobApplication application = getOwnedApplication(id);
		applyStatus(application, request.status());
		if (request.notes() != null) {
			application.setNotes(Strings.blankToNull(request.notes()));
		}
		log.info("Updated application id={} status={}", id, application.getStatus());
		return toResponse(application);
	}

	private JobApplication getOwnedApplication(Long id) {
		long userId = currentUserService.requireUserId();
		return jobApplicationRepository.findByIdAndVacancyUserId(id, userId)
				.orElseThrow(() -> new NotFoundException("Application not found: " + id));
	}

	private void applyStatus(JobApplication application, ApplicationStatus status) {
		application.setStatus(status);
		if (status == ApplicationStatus.NOT_APPLIED) {
			application.setAppliedAt(null);
		}
		else if (application.getAppliedAt() == null) {
			application.setAppliedAt(Instant.now());
		}
	}

	private JobApplicationResponse toResponse(JobApplication application) {
		return new JobApplicationResponse(
				application.getId(),
				application.getVacancy().getId(),
				application.getStatus().name(),
				application.getStatus().isApplied(),
				application.getAppliedAt() == null ? null : application.getAppliedAt().toString(),
				application.getNotes(),
				application.getCreatedAt().toString(),
				application.getUpdatedAt().toString()
		);
	}
}
