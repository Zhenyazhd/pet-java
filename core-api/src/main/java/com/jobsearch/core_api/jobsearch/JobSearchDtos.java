package com.jobsearch.core_api.jobsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** The shapes of /api/job-search. Response records also read job-list's replies and drop the fields we do not pass on. */
public final class JobSearchDtos {

	private JobSearchDtos() {
	}

	public record RegionDto(
			@NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "must be a 2-letter country code") String country,
			@Size(max = 100) String city
	) {
	}

	public record ProfileRequest(
			@NotBlank @Size(max = 5000) String expectations,
			@NotEmpty @Size(max = 30) List<@NotBlank @Size(max = 100) String> roles,
			@Size(max = 20) List<@Valid @NotNull RegionDto> regions,
			@Min(0) @Max(100) Integer minScore
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ProfileResponse(long id, String name, String expectations, List<String> roles, List<RegionDto> regions, List<String> sources,
			List<String> enabledSources, int minScore) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SourceRun(String source, String status, int fetched, int newJobs, int dropped, BigDecimal costUsd, String error) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record RunResponse(
			long id,
			String status,
			String createdAt,
			String startedAt,
			String finishedAt,
			int fetched,
			int newJobs,
			int ranked,
			BigDecimal costUsd,
			String error,
			List<SourceRun> sources,
			String mode
	) {
		/** {@code mode} is "FULL" or "RANK_ONLY"; an older job-list that omits it means a full run. */
		public RunResponse {
			mode = mode == null ? "FULL" : mode;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record JobSummary(
			long id,
			String source,
			String title,
			String company,
			String location,
			String remote,
			String contractType,
			String salary,
			String url,
			String postedAt,
			List<String> skills,
			String snippet
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record MatchResponse(long id, int score, String reason, String rankedAt, String deliveredAt, JobSummary job) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record MatchPage(List<MatchResponse> items, String nextCursor) {
	}

	/** {@code unranked} is null when the count is unknown (job-list did not answer in time). */
	public record State(boolean configured, ProfileResponse profile, RunResponse latestRun, Integer unranked) {
	}
}
