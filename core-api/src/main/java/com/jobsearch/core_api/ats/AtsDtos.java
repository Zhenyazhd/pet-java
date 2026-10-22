package com.jobsearch.core_api.ats;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public final class AtsDtos {

	private AtsDtos() {
	}

	public record MatchRequest(
			@NotBlank String vacancyContext,
			/** When set, persist {@code averageScore} onto this owned vacancy's {@code matchPercent}. */
			Long vacancyId,
			/** The finished PDF build to score: the model is given the text an ATS reads from that PDF. */
			@NotNull UUID compileJobId
	) {
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
