package com.jobsearch.core_api.jobs;

import tools.jackson.databind.JsonNode;

public final class JobDtos {

	private JobDtos() {
	}

	/** The status of any job type; {@code result} is the handler's JSON output once the job is DONE. */
	public record JobResponse(
			String id,
			String status,
			JobError error,
			JsonNode result,
			String createdAt,
			String finishedAt
	) {
	}

	public record JobError(String code, String message) {
	}
}
