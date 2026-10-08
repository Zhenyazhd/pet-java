package com.jobsearch.core_api.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class AiDtos {

	private AiDtos() {
	}

	public record ChatTurn(
			@NotBlank @Size(max = 20) String role,
			@NotBlank @Size(max = 20_000) String content
	) {
	}

	public record SuggestRequest(
			@NotBlank String section,
			@NotBlank @Size(max = 4_000) String instruction,
			Integer itemIndex,
			@Size(max = 100) List<@Valid ChatTurn> history,
			/** Pasted vacancy / job description used to tailor suggestions. Optional. */
			@Size(max = 50_000) String vacancyContext,
			/** OpenRouter model id; falls back to app.open-router.model when blank. */
			@Size(max = 100) String model
	) {
		// section: "all" = full resume focus; otherwise a resume section name
	}

	public record SuggestResponse(
			String section,
			Integer itemIndex,
			String message,
			JsonNode proposed,
			/** The model offered an edit that could not be used; {@code message} says so in English. */
			boolean proposalRejected
	) {
	}
}
