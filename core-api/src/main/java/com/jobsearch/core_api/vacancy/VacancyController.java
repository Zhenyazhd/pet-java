package com.jobsearch.core_api.vacancy;

import com.jobsearch.core_api.vacancy.VacancyDtos.VacancyRequest;
import com.jobsearch.core_api.vacancy.VacancyDtos.VacancyResponse;
import jakarta.validation.Valid;
import java.util.List;
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

@RestController
@RequestMapping("/api/vacancies")
public class VacancyController {

	private final VacancyService vacancyService;

	public VacancyController(VacancyService vacancyService) {
		this.vacancyService = vacancyService;
	}

	@GetMapping
	public List<VacancyResponse> list() {
		return vacancyService.findAll();
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
