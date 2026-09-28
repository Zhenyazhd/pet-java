package com.jobsearch.core_api.ats;

import com.jobsearch.core_api.ats.AtsDtos.MatchRequest;
import com.jobsearch.core_api.ats.AtsDtos.MatchResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Vacancy match via local ATS Screener (tools/ats-screener). */
@RestController
@RequestMapping("/api/ai")
public class AtsMatchController {

	private final AtsMatchService atsMatchService;

	public AtsMatchController(AtsMatchService atsMatchService) {
		this.atsMatchService = atsMatchService;
	}

	@PostMapping("/resume/match")
	public MatchResponse match(@Valid @RequestBody MatchRequest request) {
		return atsMatchService.match(request);
	}
}
