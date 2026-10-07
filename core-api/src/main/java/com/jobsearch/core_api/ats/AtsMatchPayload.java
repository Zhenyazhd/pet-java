package com.jobsearch.core_api.ats;

/**
 * Job payload for {@link AtsMatchHandler}. The resume is flattened to text when the user clicks, so the
 * job scores exactly what they saw then.
 */
record AtsMatchPayload(String resumeText, String vacancyText, Long vacancyId) {
}
