package com.jobsearch.joblist.run;

import java.math.BigDecimal;
import java.util.List;

public final class RunDtos {

	private RunDtos() {
	}

	public record SourceResponse(String source, String status, String apifyRunId, int fetched, int newJobs, int dropped, BigDecimal costUsd, String error) {
	}

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
			List<SourceResponse> sources
	) {
	}

	static RunResponse of(Run run, List<RunSource> sources) {
		return new RunResponse(
				run.getId(),
				run.getStatus().name(),
				run.getCreatedAt().toString(),
				run.getStartedAt() == null ? null : run.getStartedAt().toString(),
				run.getFinishedAt() == null ? null : run.getFinishedAt().toString(),
				run.getFetched(),
				run.getNewJobs(),
				run.getRanked(),
				run.getCostUsd(),
				run.getError(),
				sources.stream().map(source -> new SourceResponse(source.getSource().name(), source.getStatus().name(),
						source.getApifyRunId(), source.getFetched(), source.getNewJobs(), source.getDropped(), source.getCostUsd(), source.getError())).toList());
	}
}
