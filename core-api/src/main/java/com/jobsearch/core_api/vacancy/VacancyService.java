package com.jobsearch.core_api.vacancy;

import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.common.Strings;
import com.jobsearch.core_api.common.UniqueConstraint;
import com.jobsearch.core_api.vacancy.VacancyDtos.ApplicationRequest;
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
	private final SkillService skillService;

	public VacancyService(
			VacancyRepository vacancyRepository,
			CurrentUserService currentUserService,
			SkillService skillService
	) {
		this.vacancyRepository = vacancyRepository;
		this.skillService = skillService;
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
		Vacancy saved = saveChecked(vacancy);
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
		Vacancy saved = saveChecked(vacancy);
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

	private Vacancy saveChecked(Vacancy vacancy) {
		return UniqueConstraint.onConflict(
				"The vacancy was changed at the same time; try again",
				() -> UniqueConstraint.onConflict(
						"Vacancy with this URL already exists",
						() -> vacancyRepository.saveAndFlush(vacancy),
						UniqueConstraint.VACANCY_USER_URL
				),
				UniqueConstraint.VACANCY_REQUIREMENT_SKILL
		);
	}

	private Vacancy getOwnedVacancy(Long id) {
		long userId = currentUserService.requireUserId();
		return vacancyRepository.findDetailedByIdAndUserId(id, userId)
				.orElseThrow(() -> new NotFoundException("Vacancy not found: " + id));
	}

	private void applyRequest(Vacancy vacancy, VacancyRequest request) {
		List<RequirementRequest> requirements = request.requirements();
		List<VacancyRequirements.Item> items = requirements == null ? List.of() : requirements.stream()
				.filter(item -> item != null)
				.map(item -> new VacancyRequirements.Item(
						item.name(),
						Boolean.TRUE.equals(item.required())
				))
				.toList();
		List<SkillService.Resolved> skills = skillService.resolve(VacancyRequirements.dedupe(items, Integer.MAX_VALUE));

		vacancy.setUrl(request.url().trim());
		vacancy.setTitle(request.title().trim());
		vacancy.setCompany(Strings.blankToNull(request.company()));
		vacancy.setDescription(Strings.blankToNull(request.description()));
		vacancy.setMatchPercent(request.matchPercent());
		vacancy.syncRequirements(skills);
	}

	public ApplicationSummaryResponse updateApplication(Long id, ApplicationRequest request) {
		long userId = currentUserService.requireUserId();
		Vacancy vacancy = vacancyRepository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new NotFoundException("Vacancy not found: " + id));
		vacancy.setApplicationStatus(request.status());
		if (request.notes() != null) {
			vacancy.setNotes(Strings.blankToNull(request.notes()));
		}
		log.info("Updated application vacancyId={} userId={} status={}", id, userId, vacancy.getApplicationStatus());
		return toSummary(vacancy);
	}

	private static ApplicationSummaryResponse toSummary(Vacancy vacancy) {
		return new ApplicationSummaryResponse(
				vacancy.getApplicationStatus().name(),
				vacancy.getApplicationStatus().isApplied(),
				vacancy.getNotes()
		);
	}

	private VacancyResponse toResponse(Vacancy vacancy) {
		List<RequirementResponse> requirements = vacancy.getRequirements().stream()
				.map(item -> new RequirementResponse(item.getId(), item.getName(), item.isRequired()))
				.toList();

		return new VacancyResponse(
				vacancy.getId(),
				vacancy.getUrl(),
				vacancy.getTitle(),
				vacancy.getCompany(),
				vacancy.getDescription(),
				vacancy.getMatchPercent(),
				requirements,
				toSummary(vacancy),
				vacancy.getCreatedAt().toString(),
				vacancy.getUpdatedAt().toString()
		);
	}
}
