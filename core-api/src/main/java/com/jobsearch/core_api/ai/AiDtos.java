package com.jobsearch.core_api.ai;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class AiDtos {

	private AiDtos() {
	}

	public record ChatTurn(
			@NotBlank String role,
			@NotBlank String content
	) {
	}

	public record SuggestRequest(
			@NotBlank String section,
			@NotBlank String instruction,
			Integer itemIndex,
			List<ChatTurn> history,
			/** Pasted vacancy / job description used to tailor suggestions. Optional. */
			String vacancyContext
	) {
		// section: "all" = full resume focus; otherwise a resume section name
	}

	public record SuggestResponse(
			String section,
			Integer itemIndex,
			String message,
			JsonNode proposed
	) {
	}
}
