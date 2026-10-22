package com.jobsearch.joblist.api;

import com.jobsearch.joblist.profile.ProfileDtos.ProfileRequest;
import com.jobsearch.joblist.profile.ProfileDtos.ProfileResponse;
import com.jobsearch.joblist.profile.SearchProfileService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/profiles")
public class ProfileController {

	private final SearchProfileService service;

	public ProfileController(SearchProfileService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ProfileResponse create(@RequestAttribute(ClientAuthFilter.CLIENT) String client, @Valid @RequestBody ProfileRequest request) {
		return service.create(client, request);
	}

	@GetMapping
	List<ProfileResponse> list(@RequestAttribute(ClientAuthFilter.CLIENT) String client, @RequestParam(required = false) String ownerRef) {
		return service.list(client, ownerRef);
	}

	@GetMapping("/{id}")
	ProfileResponse get(@RequestAttribute(ClientAuthFilter.CLIENT) String client, @PathVariable long id) {
		return service.get(client, id);
	}

	@PutMapping("/{id}")
	ProfileResponse update(@RequestAttribute(ClientAuthFilter.CLIENT) String client, @PathVariable long id, @Valid @RequestBody ProfileRequest request) {
		return service.update(client, id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@RequestAttribute(ClientAuthFilter.CLIENT) String client, @PathVariable long id) {
		service.delete(client, id);
	}
}
