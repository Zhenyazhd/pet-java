package com.jobsearch.core_api.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
			@NotBlank @Size(max = 255) String displayName,
			@NotBlank @Email @Size(max = 255) String email,
			@NotNull @Size(max = 20_000) String careerPath
	) {
	}
}
