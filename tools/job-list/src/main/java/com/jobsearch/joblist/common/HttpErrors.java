package com.jobsearch.joblist.common;

import org.springframework.web.client.RestClientResponseException;

/** A short description of a failed HTTP call for logs: the status or the exception class, never the response body. */
public final class HttpErrors {

	private HttpErrors() {
	}

	public static String describe(RuntimeException ex) {
		return ex instanceof RestClientResponseException response
				? "HTTP " + response.getStatusCode().value()
				: ex.getClass().getSimpleName();
	}
}
