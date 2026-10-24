package com.jobsearch.joblist.api;

import com.jobsearch.joblist.rank.MatchDtos.MatchPage;
import com.jobsearch.joblist.rank.MatchService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class MatchController {

	private final MatchService service;

	public MatchController(MatchService service) {
		this.service = service;
	}

	@GetMapping("/profiles/{id}/matches")
	MatchPage matches(
			@RequestAttribute(ClientAuthFilter.CLIENT) String client,
			@PathVariable long id,
			@RequestParam(required = false) Integer minScore,
			@RequestParam(required = false) Boolean delivered,
			@RequestParam(required = false) String since,
			@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) String cursor,
			@RequestParam(required = false) String sort
	) {
		return service.page(client, id, new MatchService.Query(minScore, delivered, since, limit, cursor, sort));
	}

	@PostMapping("/matches/{id}/ack")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void ack(@RequestAttribute(ClientAuthFilter.CLIENT) String client, @PathVariable long id) {
		service.markDelivered(client, id);
	}
}
