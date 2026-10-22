package com.jobsearch.core_api.ai;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PromptDtos {

	private PromptDtos() {
	}

	public record PromptPartResponse(
			String key,
			String title,
			String description,
			String defaultText,
			String customText,
			boolean customized,
			boolean defaultChanged
	) {
	}

	public record PromptPartRequest(@NotNull @Size(max = 20_000) String text) {
	}
}
