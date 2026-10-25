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
			String role,
			/** False for someone who signs in only with Google: no current password to ask for, a reset link adds one. */
			boolean hasPassword,
			/** What this deployment offers, so the page can hide what is switched off instead of failing on click. */
			Features features
	) {

		public static AuthUserResponse of(AppUserPrincipal principal, Features features) {
			return new AuthUserResponse(
					principal.getId(),
					principal.getUsername(),
					principal.getDisplayName(),
					principal.getRole().name(),
					principal.getPassword() != null,
					features
			);
		}
	}

	public record Features(boolean atsMatch, boolean pdfReadability) {
	}

	public record LoginRequest(
			@NotBlank @Email String email,
			@NotBlank @Size(min = 8, max = 200) String password
	) {
	}

	/** The account exists but cannot sign in until the address is confirmed; the mail with the link is on its way. */
	public record Registered(String email) {
	}

	public record InviteRequest(@NotBlank @Size(max = 200) String inviteCode) {
	}

	/** What the signed-out pages need to know before anyone signs in. */
	public record PublicConfig(boolean google) {
	}

	public record EmailRequest(@NotBlank @Email String email) {
	}

	public record TokenRequest(@NotBlank @Size(max = 200) String token) {
	}

	public record ResetPasswordRequest(
			@NotBlank @Size(max = 200) String token,
			@NotBlank @Size(min = 8, max = 200) @MaxUtf8Bytes(72) String password
	) {
	}

	public record DeleteAccountRequest(
			@NotBlank @Email String email,
			@Size(max = 200) String password
	) {
	}

	public record RegisterRequest(
			@NotBlank @Email String email,
			@NotBlank @Size(min = 8, max = 200) @MaxUtf8Bytes(72) String password,
			@Size(max = 255) String displayName,
			@NotBlank @Size(max = 200) String inviteCode
	) {
	}
}
