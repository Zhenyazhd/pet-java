package com.jobsearch.core_api.readability;

/**
 * Job payload for {@link ReadabilityJobHandler}: the PDF is looked up by its cache key, and {@code expected} is the
 * plain text of the resume it was built from, taken when the check was requested.
 */
record ReadabilityPayload(String sourceHash, String expected) {
}
