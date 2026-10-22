package com.jobsearch.core_api.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ProfileDtos {

	/** PostgreSQL cannot store a NUL character in text; without this check it comes back as a 500. */
	private static final String NO_NUL = "[^\\u0000]*";
	private static final String NO_NUL_MESSAGE = "must not contain NUL characters";

	private ProfileDtos() {
	}

	public record ProfileResponse(
			String displayName,
			String email,
			String careerPath
	) {
	}

	public record ProfileRequest(
			@NotBlank @Size(max = 255) @Pattern(regexp = NO_NUL, message = NO_NUL_MESSAGE) String displayName,
			@NotBlank @Email @Size(max = 255) String email,
			@NotNull @Size(max = 40_000) @Pattern(regexp = NO_NUL, message = NO_NUL_MESSAGE) String careerPath
	) {
	}
}
