package com.jobsearch.core_api.vacancy;

import com.jobsearch.core_api.jobs.JobDtos.JobResponse;
import com.jobsearch.core_api.jobs.JobService;
import com.jobsearch.core_api.jobs.JobType;
import com.jobsearch.core_api.vacancy.VacancyDtos.VacancyImportRequest;
import com.jobsearch.core_api.vacancy.VacancyDtos.VacancyRequest;
import com.jobsearch.core_api.vacancy.VacancyDtos.VacancyResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Vacancy CRUD, plus import from a pasted posting queued as a VACANCY_IMPORT job. */
@RestController
@RequestMapping("/api/vacancies")
public class VacancyController {

	private final VacancyService vacancyService;
	private final VacancyImportService vacancyImportService;
	private final JobService jobService;

	public VacancyController(
			VacancyService vacancyService,
			VacancyImportService vacancyImportService,
			JobService jobService
	) {
		this.vacancyService = vacancyService;
		this.vacancyImportService = vacancyImportService;
		this.jobService = jobService;
	}

	@GetMapping
	public List<VacancyResponse> list() {
		return vacancyService.findAll();
	}

	/** Queues the LLM extraction; poll {@code GET /import/{jobId}} for {@code result.vacancyId}. */
	@PostMapping("/import")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public JobResponse enqueueImport(@Valid @RequestBody VacancyImportRequest request) {
		return vacancyImportService.enqueue(request);
	}

	@GetMapping("/import/{jobId}")
	public JobResponse getImportJob(@PathVariable UUID jobId) {
		return jobService.get(jobId, JobType.VACANCY_IMPORT);
	}

	@GetMapping("/{id}")
	public VacancyResponse get(@PathVariable Long id) {
		return vacancyService.findById(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public VacancyResponse create(@Valid @RequestBody VacancyRequest request) {
		return vacancyService.create(request);
	}

	@PutMapping("/{id}")
	public VacancyResponse update(@PathVariable Long id, @Valid @RequestBody VacancyRequest request) {
		return vacancyService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		vacancyService.delete(id);
	}
}
