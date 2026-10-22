package com.jobsearch.joblist.run;

public enum RunStatus {
	QUEUED,
	RUNNING,
	SUCCEEDED,
	/** Some sources failed, or ranking failed, but others worked. */
	PARTIAL,
	FAILED
}
