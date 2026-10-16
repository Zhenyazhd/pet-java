package com.jobsearch.joblist.rank;

import java.util.List;

public final class MatchDtos {

	private MatchDtos() {
	}

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

	public record MatchResponse(long id, int score, String reason, String rankedAt, String deliveredAt, JobSummary job) {
	}

	public record MatchPage(List<MatchResponse> items, String nextCursor) {
	}
}
