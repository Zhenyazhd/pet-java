package com.jobsearch.core_api.vacancy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class VacancyDtos {

	private VacancyDtos() {
	}

	public record RequirementRequest(
			@NotBlank @Size(max = 255) String name,
			@NotNull Boolean required
	) {
	}

	public record VacancyRequest(
			@NotBlank String url,
			@NotBlank @Size(max = 255) String title,
			@Size(max = 255) String company,
			String description,
			@Min(0) @Max(100) Integer matchPercent,
			List<@Valid RequirementRequest> requirements
	) {
	}

	public record RequirementResponse(
			Long id,
			String name,
			boolean required
	) {
	}

	public record ApplicationSummaryResponse(
			Long id,
			String status,
			boolean applied
	) {
	}

	public record VacancyResponse(
			Long id,
			String url,
			String title,
			String company,
			String description,
			Integer matchPercent,
			List<RequirementResponse> requirements,
			ApplicationSummaryResponse application,
			String createdAt,
			String updatedAt
	) {
	}
}
