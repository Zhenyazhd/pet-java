package com.jobsearch.core_api.auth;

import com.jobsearch.core_api.auth.InviteCodeDtos.InviteCodeResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invite-codes")
@PreAuthorize("hasRole('ADMIN')")
public class InviteCodeController {

	private final InviteCodeService inviteCodeService;

	public InviteCodeController(InviteCodeService inviteCodeService) {
		this.inviteCodeService = inviteCodeService;
	}

	@GetMapping
	public List<InviteCodeResponse> list() {
		return inviteCodeService.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public InviteCodeResponse create() {
		return inviteCodeService.create();
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		inviteCodeService.deleteUnused(id);
	}
}
