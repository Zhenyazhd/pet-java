package com.jobsearch.core_api.vacancy;

import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.common.Strings;
import com.jobsearch.core_api.common.UniqueConstraint;
import com.jobsearch.core_api.jobapplication.JobApplication;
import com.jobsearch.core_api.vacancy.VacancyDtos.ApplicationSummaryResponse;
import com.jobsearch.core_api.vacancy.VacancyDtos.RequirementRequest;
import com.jobsearch.core_api.vacancy.VacancyDtos.RequirementResponse;
import com.jobsearch.core_api.vacancy.VacancyDtos.VacancyRequest;
import com.jobsearch.core_api.vacancy.VacancyDtos.VacancyResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Vacancy CRUD scoped to the authenticated owner. */
@Service
@Transactional
public class VacancyService {

	private static final Logger log = LoggerFactory.getLogger(VacancyService.class);

	private final VacancyRepository vacancyRepository;
	private final CurrentUserService currentUserService;

	public VacancyService(VacancyRepository vacancyRepository, CurrentUserService currentUserService) {
		this.vacancyRepository = vacancyRepository;
		this.currentUserService = currentUserService;
	}

	@Transactional(readOnly = true)
	public List<VacancyResponse> findAll() {
		long userId = currentUserService.requireUserId();
		return vacancyRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public VacancyResponse findById(Long id) {
		return toResponse(getOwnedVacancy(id));
	}

	public VacancyResponse create(VacancyRequest request) {
		long userId = currentUserService.requireUserId();
		Vacancy vacancy = new Vacancy();
		vacancy.setUserId(userId);
		applyRequest(vacancy, request);
		vacancy.setApplication(new JobApplication());
		Vacancy saved = UniqueConstraint.onConflict(
				"Vacancy with this URL already exists",
				() -> vacancyRepository.saveAndFlush(vacancy),
				UniqueConstraint.VACANCY_USER_URL
		);
		log.info(
				"Created vacancy id={} userId={} title={} applicationStatus=NOT_APPLIED",
				saved.getId(),
				userId,
				saved.getTitle()
		);
		return toResponse(saved);
	}

	public VacancyResponse update(Long id, VacancyRequest request) {
		long userId = currentUserService.requireUserId();
		Vacancy vacancy = getOwnedVacancy(id);
		applyRequest(vacancy, request);
		Vacancy saved = UniqueConstraint.onConflict(
				"Vacancy with this URL already exists",
				() -> vacancyRepository.saveAndFlush(vacancy),
				UniqueConstraint.VACANCY_USER_URL
		);
		log.info("Updated vacancy id={} userId={}", id, userId);
		return toResponse(saved);
	}

	public void delete(Long id) {
		long userId = currentUserService.requireUserId();
		if (!vacancyRepository.existsByIdAndUserId(id, userId)) {
			throw new NotFoundException("Vacancy not found: " + id);
		}
		vacancyRepository.deleteById(id);
		log.info("Deleted vacancy id={} userId={}", id, userId);
	}

	private Vacancy getOwnedVacancy(Long id) {
		long userId = currentUserService.requireUserId();
		return vacancyRepository.findDetailedByIdAndUserId(id, userId)
				.orElseThrow(() -> new NotFoundException("Vacancy not found: " + id));
	}

	private void applyRequest(Vacancy vacancy, VacancyRequest request) {
		vacancy.setUrl(request.url().trim());
		vacancy.setTitle(request.title().trim());
		vacancy.setCompany(Strings.blankToNull(request.company()));
		vacancy.setDescription(Strings.blankToNull(request.description()));
		vacancy.setMatchPercent(request.matchPercent());

		vacancy.clearRequirements();
		List<RequirementRequest> requirements = request.requirements();
		if (requirements != null) {
			List<VacancyRequirements.Item> items = requirements.stream()
					.filter(item -> item != null)
					.map(item -> new VacancyRequirements.Item(
							item.name(),
							Boolean.TRUE.equals(item.required())
					))
					.toList();
			for (VacancyRequirements.Item item : VacancyRequirements.dedupe(items, Integer.MAX_VALUE)) {
				vacancy.addRequirement(item.name(), item.required());
			}
		}
	}

	VacancyResponse toResponse(Vacancy vacancy) {
		List<RequirementResponse> requirements = vacancy.getRequirements().stream()
				.map(item -> new RequirementResponse(item.getId(), item.getName(), item.isRequired()))
				.toList();

		JobApplication application = vacancy.getApplication();
		ApplicationSummaryResponse applicationSummary = null;
		if (application != null) {
			applicationSummary = new ApplicationSummaryResponse(
					application.getId(),
					application.getStatus().name(),
					application.getStatus().isApplied(),
					application.getNotes()
			);
		}

		return new VacancyResponse(
				vacancy.getId(),
				vacancy.getUrl(),
				vacancy.getTitle(),
				vacancy.getCompany(),
				vacancy.getDescription(),
				vacancy.getMatchPercent(),
				requirements,
				applicationSummary,
				vacancy.getCreatedAt().toString(),
				vacancy.getUpdatedAt().toString()
		);
	}
}
