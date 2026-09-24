package com.jobsearch.core_api.jobapplication;

import jakarta.validation.constraints.NotNull;

public final class JobApplicationDtos {

	private JobApplicationDtos() {
	}

	public record JobApplicationRequest(
			@NotNull Long vacancyId,
			@NotNull ApplicationStatus status,
			String notes
	) {
	}

	public record JobApplicationUpdateRequest(
			@NotNull ApplicationStatus status,
			String notes
	) {
	}

	public record JobApplicationResponse(
			Long id,
			Long vacancyId,
			String status,
			boolean applied,
			String appliedAt,
			String notes,
			String createdAt,
			String updatedAt
	) {
	}
}
