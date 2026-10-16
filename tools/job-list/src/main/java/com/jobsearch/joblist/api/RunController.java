package com.jobsearch.joblist.api;

import com.jobsearch.joblist.run.RunDtos.RunResponse;
import com.jobsearch.joblist.run.RunService;
import com.jobsearch.joblist.run.UsageService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class RunController {

	private final RunService runs;
	private final UsageService usage;

	public RunController(RunService runs, UsageService usage) {
		this.runs = runs;
		this.usage = usage;
	}

	@PostMapping("/profiles/{id}/runs")
	@ResponseStatus(HttpStatus.ACCEPTED)
	RunResponse start(@RequestAttribute(ClientAuthFilter.CLIENT) String client, @PathVariable long id) {
		return runs.start(client, id);
	}

	@GetMapping("/profiles/{id}/runs")
	List<RunResponse> recent(@RequestAttribute(ClientAuthFilter.CLIENT) String client, @PathVariable long id) {
		return runs.recent(client, id);
	}

	@GetMapping("/usage")
	UsageService.Usage usage() {
		return usage.current();
	}
}
