package com.jobsearch.core_api.ats;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class AtsDtos {

	private AtsDtos() {
	}

	public record MatchRequest(
			@NotBlank String vacancyContext,
			/** When set, persist {@code averageScore} onto this owned vacancy's {@code matchPercent}. */
			Long vacancyId
	) {
		public MatchRequest(String vacancyContext) {
			this(vacancyContext, null);
		}
	}

	public record PlatformScore(
			String system,
			String vendor,
			int overallScore,
			boolean passesFilter
	) {
	}

	public record Suggestion(
			String summary,
			List<String> details,
			String impact,
			List<String> platforms
	) {
	}

	public record MatchResponse(
			int averageScore,
			List<PlatformScore> platforms,
			List<Suggestion> suggestions,
			String provider,
			boolean cached,
			String summary
	) {
	}
}
