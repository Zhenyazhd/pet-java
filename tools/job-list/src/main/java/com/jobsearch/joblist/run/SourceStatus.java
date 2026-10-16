package com.jobsearch.joblist.run;

public enum SourceStatus {
	SUCCEEDED,
	FAILED,
	/** Not called: no supported region, called too recently, or the monthly budget would be exceeded. */
	SKIPPED
}
