package com.jobsearch.core_api.ai;

import com.jobsearch.core_api.ai.PromptDtos.PromptPartRequest;
import com.jobsearch.core_api.ai.PromptDtos.PromptPartResponse;
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

/** The editable parts of the AI chat prompt. Admin only (see SecurityConfig). */
@RestController
@RequestMapping("/api/profile/prompts")
public class PromptController {

	private final UserPromptService userPromptService;

	public PromptController(UserPromptService userPromptService) {
		this.userPromptService = userPromptService;
	}

	@GetMapping
	public List<PromptPartResponse> list() {
		return userPromptService.list();
	}

	@PutMapping("/{key}")
	public PromptPartResponse save(@PathVariable String key, @Valid @RequestBody PromptPartRequest request) {
		return userPromptService.save(key, request.text());
	}

	/** Makes the caller's saved wording the default for every user. */
	@PostMapping("/publish")
	public List<PromptPartResponse> publish() {
		return userPromptService.publish();
	}

	@DeleteMapping("/{key}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reset(@PathVariable String key) {
		userPromptService.reset(key);
	}
}
