package com.jobsearch.joblist.rank;

/** One validated answer of the model. */
record RankedJob(long jobId, int score, String reason) {
}
