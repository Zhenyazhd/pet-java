package com.jobsearch.core_api.vacancy;

import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.jobapplication.ApplicationStatus;
import com.jobsearch.core_api.jobapplication.JobApplication;
import com.jobsearch.core_api.vacancy.VacancyDtos.ApplicationSummaryResponse;
import com.jobsearch.core_api.vacancy.VacancyDtos.RequirementRequest;
import com.jobsearch.core_api.vacancy.VacancyDtos.RequirementResponse;
import com.jobsearch.core_api.vacancy.VacancyDtos.VacancyRequest;
import com.jobsearch.core_api.vacancy.VacancyDtos.VacancyResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class VacancyService {

	private final VacancyRepository vacancyRepository;

	public VacancyService(VacancyRepository vacancyRepository) {
		this.vacancyRepository = vacancyRepository;
	}

	@Transactional(readOnly = true)
	public List<VacancyResponse> findAll() {
		return vacancyRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public VacancyResponse findById(Long id) {
		return toResponse(getVacancy(id));
	}

	public VacancyResponse create(VacancyRequest request) {
		if (vacancyRepository.existsByUrl(request.url())) {
			throw new ConflictException("Vacancy with this URL already exists");
		}
		Vacancy vacancy = new Vacancy();
		applyRequest(vacancy, request);
		return toResponse(vacancyRepository.save(vacancy));
	}

	public VacancyResponse update(Long id, VacancyRequest request) {
		Vacancy vacancy = getVacancy(id);
		if (!vacancy.getUrl().equals(request.url()) && vacancyRepository.existsByUrl(request.url())) {
			throw new ConflictException("Vacancy with this URL already exists");
		}
		applyRequest(vacancy, request);
		return toResponse(vacancy);
	}

	public void delete(Long id) {
		if (!vacancyRepository.existsById(id)) {
			throw new NotFoundException("Vacancy not found: " + id);
		}
		vacancyRepository.deleteById(id);
	}

	Vacancy getVacancy(Long id) {
		return vacancyRepository.findDetailedById(id)
				.orElseThrow(() -> new NotFoundException("Vacancy not found: " + id));
	}

	private void applyRequest(Vacancy vacancy, VacancyRequest request) {
		vacancy.setUrl(request.url().trim());
		vacancy.setTitle(request.title().trim());
		vacancy.setCompany(blankToNull(request.company()));
		vacancy.setDescription(blankToNull(request.description()));
		vacancy.setMatchPercent(request.matchPercent());

		vacancy.clearRequirements();
		List<RequirementRequest> requirements = request.requirements();
		if (requirements != null) {
			for (RequirementRequest item : requirements) {
				VacancyRequirement requirement = new VacancyRequirement();
				requirement.setName(item.name().trim());
				requirement.setRequired(Boolean.TRUE.equals(item.required()));
				vacancy.addRequirement(requirement);
			}
		}
	}

	private VacancyResponse toResponse(Vacancy vacancy) {
		List<RequirementResponse> requirements = vacancy.getRequirements().stream()
				.map(item -> new RequirementResponse(item.getId(), item.getName(), item.isRequired()))
				.toList();

		JobApplication application = vacancy.getApplication();
		ApplicationSummaryResponse applicationSummary = null;
		if (application != null) {
			applicationSummary = new ApplicationSummaryResponse(
					application.getId(),
					application.getStatus().name(),
					isApplied(application.getStatus())
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

	private static boolean isApplied(ApplicationStatus status) {
		return status != ApplicationStatus.NOT_APPLIED;
	}

	private static String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
