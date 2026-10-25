package com.jobsearch.core_api.jobsearch;

import com.jobsearch.core_api.common.BadRequestException;
import com.jobsearch.core_api.jobsearch.JobSearchDtos.MatchPage;
import com.jobsearch.core_api.jobsearch.JobSearchDtos.ProfileRequest;
import com.jobsearch.core_api.jobsearch.JobSearchDtos.ProfileResponse;
import com.jobsearch.core_api.jobsearch.JobSearchDtos.RunResponse;
import com.jobsearch.core_api.jobsearch.JobSearchDtos.State;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Vacancies that fit the signed-in user, found and ranked by job-list. */
@RestController
@RequestMapping("/api/job-search")
public class JobSearchController {

	private static final int MAX_CURSOR_CHARS = 100;

	private final JobSearchService service;

	public JobSearchController(JobSearchService service) {
		this.service = service;
	}

	@GetMapping
	public State state() {
		return service.state();
	}

	@PutMapping("/profile")
	public ProfileResponse saveProfile(@Valid @RequestBody ProfileRequest request) {
		return service.saveProfile(request);
	}

	@PostMapping("/runs")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public RunResponse startRun() {
		return service.startRun();
	}

	/** Scores the already stored postings; calls no job board. */
	@PostMapping("/runs/rank")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public RunResponse startRankRun() {
		return service.startRankRun();
	}

	@GetMapping("/matches")
	public MatchPage matches(
			@RequestParam(required = false) Integer minScore,
			@RequestParam(required = false) Boolean delivered,
			@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) String cursor,
			@RequestParam(required = false) String sort
	) {
		if (cursor != null && (cursor.isBlank() || cursor.length() > MAX_CURSOR_CHARS)) {
			throw new BadRequestException("cursor must be the nextCursor of a previous page");
		}
		if (sort != null && !sort.equals("score") && !sort.equals("posted")) {
			throw new BadRequestException("sort must be score or posted");
		}
		return service.matches(minScore, delivered, limit, cursor, sort);
	}

	@PostMapping("/matches/{id}/seen")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void seen(@PathVariable long id) {
		service.markSeen(id);
	}
}
