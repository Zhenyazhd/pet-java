package com.jobsearch.joblist.run;

/** The run was released by the janitor while its worker was still going; the worker must stop and write nothing more. */
public class RunLostException extends RuntimeException {

	public RunLostException(long runId) {
		super("Run " + runId + " was released");
	}
}
