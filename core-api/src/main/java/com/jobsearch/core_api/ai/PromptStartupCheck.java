package com.jobsearch.core_api.ai;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** Loads every prompt resource at startup, so a missing or unreadable file fails the start, not the first chat. */
@Component
class PromptStartupCheck {

	@PostConstruct
	void load() {
		if (PromptPart.values().length == 0 || SystemPromptComposer.defaultPrompt().isBlank()) {
			throw new IllegalStateException("No prompt text");
		}
	}
}
