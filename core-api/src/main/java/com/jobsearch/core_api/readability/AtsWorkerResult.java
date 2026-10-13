package com.jobsearch.core_api.readability;

import tools.jackson.databind.JsonNode;

/** What {@link AtsWorkerClient} got back from the worker; {@link ReadabilityJobHandler} maps it to a JobOutcome. */
public sealed interface AtsWorkerResult {

	/** The worker checked the PDF; {@code report} is its JSON answer. */
	record Report(JsonNode report) implements AtsWorkerResult {
	}

	/** The worker refused this PDF (not a PDF, encrypted, too long...); retrying will not help. */
	record Rejected(String code, String message) implements AtsWorkerResult {
	}

	/** Every check slot on the worker is taken; try again after {@code retryAfterSeconds}. */
	record Busy(long retryAfterSeconds) implements AtsWorkerResult {
	}

	/** Timeout, worker error or network failure; worth retrying. */
	record Unavailable(String reason) implements AtsWorkerResult {
	}
}
