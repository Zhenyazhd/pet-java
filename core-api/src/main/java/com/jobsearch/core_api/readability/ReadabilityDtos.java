package com.jobsearch.core_api.readability;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public final class ReadabilityDtos {

	private ReadabilityDtos() {
	}

	/** The finished PDF build to check. */
	public record ReadabilityRequest(@NotNull UUID compileJobId) {
	}
}
