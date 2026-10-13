package com.jobsearch.atsworker;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** How much an issue matters: an error makes the verdict "issues", a warning "warnings", info nothing. */
enum Severity {
	ERROR, WARNING, INFO;

	/** The wire form is lower case, as the API has always had it. */
	@JsonValue
	String wire() {
		return name().toLowerCase(Locale.ROOT);
	}
}
