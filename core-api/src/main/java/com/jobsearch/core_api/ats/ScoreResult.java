package com.jobsearch.core_api.ats;

import tools.jackson.databind.JsonNode;

/** What {@link AtsScoreClient} got back from the ats-worker; {@link AtsMatchHandler} maps it to a JobOutcome. */
public sealed interface ScoreResult {

	/** The worker scored the resume; {@code root} is its JSON answer (the model's six platform results). */
	record Report(JsonNode root) implements ScoreResult {
	}

	/** The worker answered with an error or did not answer in time; {@code message} is shown to the user. */
	record Failed(String message) implements ScoreResult {
	}

	/** Every scoring slot on the worker is taken; try again after {@code retryAfterSeconds}, without using up an attempt. */
	record Busy(long retryAfterSeconds) implements ScoreResult {
	}

	/** The worker could not be reached or failed in a way that may pass; worth another try. */
	record Unavailable(String reason) implements ScoreResult {
	}
}
