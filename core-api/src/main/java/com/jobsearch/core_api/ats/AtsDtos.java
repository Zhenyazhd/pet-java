package com.jobsearch.core_api.ats;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class AtsDtos {

	private AtsDtos() {
	}

	public record MatchRequest(
			@NotBlank @Size(max = 50_000) String vacancyContext,
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
			boolean passesFilter,
			/** Why the score is what it is, per dimension; null when the model gave no usable detail. */
			Breakdown breakdown
	) {

		public PlatformScore(String system, String vendor, int overallScore, boolean passesFilter) {
			this(system, vendor, overallScore, passesFilter, null);
		}
	}

	/** One platform's view of the resume: what works and what is missing, per scored dimension. */
	public record Breakdown(Dimension formatting, Dimension keywords, Dimension sections, Dimension experience, Dimension education) {
	}

	public record Dimension(Integer score, List<String> good, List<String> missing) {
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
