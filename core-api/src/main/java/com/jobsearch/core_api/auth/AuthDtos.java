package com.jobsearch.core_api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

	private AuthDtos() {
	}

	public record AuthUserResponse(
			Long id,
			String email,
			String displayName,
			String role
	) {
	}

	public record LoginRequest(
			@NotBlank @Email String email,
			@NotBlank @Size(min = 8, max = 200) String password
	) {
	}

	public record RegisterRequest(
			@NotBlank @Email String email,
			@NotBlank @Size(min = 8, max = 200) String password,
			@Size(max = 255) String displayName,
			@NotBlank @Size(max = 200) String inviteCode
	) {
	}
}
