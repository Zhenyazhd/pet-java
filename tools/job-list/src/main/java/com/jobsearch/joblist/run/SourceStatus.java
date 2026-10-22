package com.jobsearch.joblist.run;

public enum SourceStatus {
	/** The actor is running now. */
	RUNNING,
	SUCCEEDED,
	FAILED,
	/** Not called: no supported region, called too recently, or the monthly budget would be exceeded. */
	SKIPPED
}
