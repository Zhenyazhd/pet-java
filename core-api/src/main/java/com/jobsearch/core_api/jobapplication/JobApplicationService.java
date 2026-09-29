package com.jobsearch.core_api.jobapplication;

import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.jobapplication.JobApplicationDtos.JobApplicationRequest;
import com.jobsearch.core_api.jobapplication.JobApplicationDtos.JobApplicationResponse;
import com.jobsearch.core_api.jobapplication.JobApplicationDtos.JobApplicationUpdateRequest;
import com.jobsearch.core_api.vacancy.Vacancy;
import com.jobsearch.core_api.vacancy.VacancyRepository;
import java.time.Instant;
import java.util.List;
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

	@Transactional(readOnly = true)
	public List<JobApplicationResponse> findAll() {
		long userId = currentUserService.requireUserId();
		return jobApplicationRepository.findAllByVacancyUserId(userId).stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public JobApplicationResponse findById(Long id) {
		return toResponse(getOwnedApplication(id));
	}

	public JobApplicationResponse create(JobApplicationRequest request) {
		long userId = currentUserService.requireUserId();
		Vacancy vacancy = vacancyRepository.findByIdAndUserId(request.vacancyId(), userId)
				.orElseThrow(() -> new NotFoundException("Vacancy not found: " + request.vacancyId()));
		if (jobApplicationRepository.existsByVacancyId(request.vacancyId())) {
			throw new ConflictException("Application for this vacancy already exists");
		}

		JobApplication application = new JobApplication();
		application.setVacancy(vacancy);
		applyStatus(application, request.status());
		application.setNotes(blankToNull(request.notes()));
		vacancy.setApplication(application);

		JobApplication saved = jobApplicationRepository.save(application);
		log.info(
				"Created application id={} vacancyId={} userId={} status={}",
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
			application.setNotes(blankToNull(request.notes()));
		}
		log.info("Updated application id={} status={}", id, application.getStatus());
		return toResponse(application);
	}

	public void delete(Long id) {
		JobApplication application = getOwnedApplication(id);
		Vacancy vacancy = application.getVacancy();
		if (vacancy != null) {
			vacancy.setApplication(null);
		}
		jobApplicationRepository.delete(application);
		log.info("Deleted application id={}", id);
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
				application.getStatus() != ApplicationStatus.NOT_APPLIED,
				application.getAppliedAt() == null ? null : application.getAppliedAt().toString(),
				application.getNotes(),
				application.getCreatedAt().toString(),
				application.getUpdatedAt().toString()
		);
	}

	private static String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
