package com.jobsearch.core_api.jobs;

/** Kinds of background work; each has exactly one {@link JobHandler}. */
public enum JobType {
	RESUME_PDF,
	ATS_MATCH,
	VACANCY_IMPORT
}
