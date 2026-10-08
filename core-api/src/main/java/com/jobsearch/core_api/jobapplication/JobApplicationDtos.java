package com.jobsearch.core_api.jobapplication;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class JobApplicationDtos {

	private JobApplicationDtos() {
	}

	public record JobApplicationRequest(
			@NotNull Long vacancyId,
			@NotNull ApplicationStatus status,
			@Size(max = 10_000) String notes
	) {
	}

	public record JobApplicationUpdateRequest(
			@NotNull ApplicationStatus status,
			@Size(max = 10_000) String notes
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
