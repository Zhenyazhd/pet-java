package com.jobsearch.core_api.profile;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PromptDtos {

	private PromptDtos() {
	}

	/** {@code customText} is null and {@code customized} false while the part has the default wording. */
	public record PromptPartResponse(
			String key,
			String title,
			String description,
			String defaultText,
			String customText,
			boolean customized,
			/** Customised, and the default has been edited since the user saved: worth a look at the new default. */
			boolean defaultChanged
	) {
	}

	public record PromptPartRequest(@NotNull @Size(max = 20_000) String text) {
	}
}
