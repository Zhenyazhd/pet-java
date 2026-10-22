package com.jobsearch.core_api.ats;

import com.jobsearch.core_api.ats.AtsDtos.MatchRequest;
import com.jobsearch.core_api.jobs.JobDtos.JobResponse;
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

/** Vacancy match via tools/ats-worker (an LLM scores the resume on six ATS platforms): queue a match, then poll for the report. */
@RestController
@RequestMapping("/api/ai/resume/match")
public class AtsMatchController {

	private final AtsMatchService atsMatchService;

	public AtsMatchController(AtsMatchService atsMatchService) {
		this.atsMatchService = atsMatchService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public JobResponse enqueue(@Valid @RequestBody MatchRequest request) {
		return atsMatchService.enqueue(request);
	}

	/** {@code result} holds the {@link AtsDtos.MatchResponse} once the job is DONE. */
	@GetMapping("/{jobId}")
	public JobResponse get(@PathVariable UUID jobId) {
		return atsMatchService.get(jobId);
	}
}
