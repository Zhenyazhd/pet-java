package com.jobsearch.core_api.cv;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CvDtos {

	private CvDtos() {
	}

	public record CvVersionResponse(
			Long id,
			String label,
			String originalFilename,
			String contentType,
			long sizeBytes,
			String createdAt
	) {
	}

	public record SendCvRequest(
			@NotNull Long cvVersionId,
			@Size(max = 2000) String notes
	) {
	}

	public record ApplicationCvResponse(
			Long id,
			Long vacancyId,
			Long jobApplicationId,
			Long cvVersionId,
			String company,
			String notes,
			String sentAt
	) {
	}
}
