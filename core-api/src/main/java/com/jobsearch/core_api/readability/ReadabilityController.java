package com.jobsearch.core_api.readability;

import com.jobsearch.core_api.jobs.JobDtos.JobResponse;
import com.jobsearch.core_api.jobs.JobService;
import com.jobsearch.core_api.jobs.JobType;
import com.jobsearch.core_api.readability.ReadabilityDtos.ReadabilityRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** "Can an ATS read my PDF?": queue a check of a built PDF, then poll for the report. */
@RestController
@RequestMapping("/api/resume/readability")
public class ReadabilityController {

	private final ReadabilityService readabilityService;
	private final JobService jobService;

	public ReadabilityController(ReadabilityService readabilityService, JobService jobService) {
		this.readabilityService = readabilityService;
		this.jobService = jobService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public JobResponse enqueue(@Valid @RequestBody ReadabilityRequest request) {
		return readabilityService.enqueue(request.compileJobId());
	}

	/** {@code result} holds the worker's report (verdict, score, issues, extracted text...) once the job is DONE. */
	@GetMapping("/{jobId}")
	public JobResponse get(@PathVariable UUID jobId) {
		return jobService.get(jobId, JobType.PDF_READABILITY);
	}
}
