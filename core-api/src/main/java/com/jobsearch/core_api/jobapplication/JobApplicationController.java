package com.jobsearch.core_api.jobapplication;

import com.jobsearch.core_api.jobapplication.JobApplicationDtos.JobApplicationRequest;
import com.jobsearch.core_api.jobapplication.JobApplicationDtos.JobApplicationResponse;
import com.jobsearch.core_api.jobapplication.JobApplicationDtos.JobApplicationUpdateRequest;
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
@RequestMapping("/api/applications")
public class JobApplicationController {

	private final JobApplicationService jobApplicationService;

	public JobApplicationController(JobApplicationService jobApplicationService) {
		this.jobApplicationService = jobApplicationService;
	}

	@GetMapping
	public List<JobApplicationResponse> list() {
		return jobApplicationService.findAll();
	}

	@GetMapping("/{id}")
	public JobApplicationResponse get(@PathVariable Long id) {
		return jobApplicationService.findById(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public JobApplicationResponse create(@Valid @RequestBody JobApplicationRequest request) {
		return jobApplicationService.create(request);
	}

	@PutMapping("/{id}")
	public JobApplicationResponse update(
			@PathVariable Long id,
			@Valid @RequestBody JobApplicationUpdateRequest request
	) {
		return jobApplicationService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		jobApplicationService.delete(id);
	}
}
