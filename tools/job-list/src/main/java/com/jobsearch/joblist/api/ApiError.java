package com.jobsearch.joblist.api;

/** The one error body the API returns. */
public record ApiError(int status, String error, String message) {
}
