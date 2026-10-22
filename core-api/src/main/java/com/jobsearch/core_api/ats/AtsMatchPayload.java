package com.jobsearch.core_api.ats;

/**
 * Job payload for {@link AtsMatchHandler}. The PDF is looked up by its cache key; it was built from the resume the
 * user saw when they clicked, so the job scores exactly that.
 */
record AtsMatchPayload(String sourceHash, String vacancyText, Long vacancyId) {
}
