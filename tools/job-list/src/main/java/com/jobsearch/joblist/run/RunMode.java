package com.jobsearch.joblist.run;

/** FULL fetches from the sources and then ranks; RANK_ONLY only ranks what is already stored (no Apify). */
public enum RunMode {
	FULL,
	RANK_ONLY
}
