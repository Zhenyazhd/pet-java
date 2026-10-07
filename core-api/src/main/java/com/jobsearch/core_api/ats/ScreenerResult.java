package com.jobsearch.core_api.ats;

import tools.jackson.databind.JsonNode;

/** What {@link AtsScreenerClient} got back from ATS Screener; {@link AtsMatchHandler} maps it to a JobOutcome. */
public sealed interface ScreenerResult {

	/** The screener scored the resume; {@code root} is its raw JSON answer. */
	record Report(JsonNode root) implements ScreenerResult {
	}

	/** The screener answered with an error or did not answer in time; {@code message} is shown to the user. */
	record Failed(String message) implements ScreenerResult {
	}

	/** The screener could not be reached at all; worth another try. */
	record Unavailable(String reason) implements ScreenerResult {
	}
}
