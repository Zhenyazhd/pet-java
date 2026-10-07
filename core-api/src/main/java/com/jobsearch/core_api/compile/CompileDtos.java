package com.jobsearch.core_api.compile;

public final class CompileDtos {

	private CompileDtos() {
	}

	public record CompileJobResponse(
			String id,
			String status,
			CompileError error,
			String createdAt,
			String finishedAt
	) {
	}

	public record CompileError(String code, String message) {
	}
}
