package com.jobsearch.core_api.ai;

import java.time.Duration;

/** What {@link OpenRouterClient#complete} got back; each failure carries a short reason for logs. */
public sealed interface ChatResult {

	/** The model's message content, with Markdown code fences already stripped. */
	record Answer(String content) implements ChatResult {
	}

	/** OpenRouter rate-limited us, or this instance already has its maximum of calls in flight. */
	record Busy(Duration retryAfter) implements ChatResult {
	}

	/** OpenRouter refused the request itself (too long, malformed); the same request will fail again. */
	record Rejected(String reason) implements ChatResult {
	}

	/** Missing, wrong or out-of-credit API key: an operator has to fix it, retrying will not help. */
	record Misconfigured(String reason) implements ChatResult {
	}

	/** Timeout, network error, OpenRouter server error or an unreadable answer; worth another try. */
	record Unavailable(String reason) implements ChatResult {
	}
}
