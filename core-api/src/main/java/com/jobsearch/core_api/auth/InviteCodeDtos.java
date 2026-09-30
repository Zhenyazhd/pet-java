package com.jobsearch.core_api.auth;

public final class InviteCodeDtos {

	private InviteCodeDtos() {
	}

	public record InviteCodeResponse(
			Long id,
			String code,
			String createdAt,
			Long createdByUserId,
			String usedAt,
			Long usedByUserId,
			boolean available
	) {
	}
}
