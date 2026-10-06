package com.jobsearch.core_api.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class ProfileDtos {

	private ProfileDtos() {
	}

	public record ProfileResponse(
			String displayName,
			String email,
			String careerPath
	) {
	}

	public record ProfileRequest(
			@NotBlank String displayName,
			@NotBlank @Email String email,
			@NotNull String careerPath
	) {
	}
}
