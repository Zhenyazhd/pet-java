package com.jobsearch.core_api.ai;

import com.jobsearch.core_api.ai.AiDtos.SuggestRequest;
import com.jobsearch.core_api.ai.AiDtos.SuggestResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI endpoints for resume section chat / suggested edits. */
@RestController
@RequestMapping("/api/ai")
public class AiController {

	private final ResumeAiService resumeAiService;

	public AiController(ResumeAiService resumeAiService) {
		this.resumeAiService = resumeAiService;
	}

	@PostMapping("/resume/suggest")
	public SuggestResponse suggest(@Valid @RequestBody SuggestRequest request) {
		return resumeAiService.suggest(request);
	}
}
