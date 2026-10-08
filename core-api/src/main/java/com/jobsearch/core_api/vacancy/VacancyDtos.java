package com.jobsearch.core_api.vacancy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class VacancyDtos {

	static final String HTTP_URL = "(?i)^https?://\\S+$";

	private VacancyDtos() {
	}

	public record RequirementRequest(
			@NotBlank @Size(max = 255) String name,
			@NotNull Boolean required
	) {
	}

	public record VacancyRequest(
			@NotBlank @Size(max = 2_000) @Pattern(regexp = HTTP_URL, message = "must be an http(s) link") String url,
			@NotBlank @Size(max = 255) String title,
			@Size(max = 255) String company,
			@Size(max = 50_000) String description,
			@Min(0) @Max(100) Integer matchPercent,
			@Size(max = 100) List<@Valid RequirementRequest> requirements
	) {
	}

	/** URL + raw job posting paste; LLM extracts title / company / description. */
	public record VacancyImportRequest(
			@NotBlank @Size(max = 2_000) @Pattern(regexp = HTTP_URL, message = "must be an http(s) link") String url,
			@NotBlank @Size(max = 50_000) String pastedText
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
			boolean applied,
			String notes
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
