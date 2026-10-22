package com.jobsearch.core_api.vacancy;

public enum ApplicationStatus {
	NOT_APPLIED,
	APPLIED,
	INTERVIEW,
	OFFER,
	REJECTED,
	WITHDRAWN;

	public boolean isApplied() {
		return this != NOT_APPLIED;
	}
}
