package com.jobsearch.joblist.source;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import tools.jackson.databind.JsonNode;

/** Reading fields of an actor's item the same way in every mapper; absent or blank values are null. */
public final class JsonFields {

	private JsonFields() {
	}

	/** A text field, stripped; a number (an id, for example) is read as its text. */
	public static String text(JsonNode item, String field) {
		JsonNode node = item.path(field);
		if (node.isNumber()) {
			return node.asString();
		}
		return node.isString() && !node.asString().isBlank() ? node.asString().strip() : null;
	}

	public static BigDecimal decimal(JsonNode item, String field) {
		return item.path(field).isNumber() ? item.get(field).decimalValue() : null;
	}

	/**
	 * A posting date as an instant: an ISO instant ("2026-10-17T08:00:00Z"), a date-time without a zone (read as UTC) or a
	 * date alone ("2026-10-17", read as the START of that day in UTC, the conservative choice for the age limit).
	 * Null when blank or not a date.
	 */
	public static Instant instant(String value) {
		if (value == null) {
			return null;
		}
		try {
			return Instant.parse(value);
		}
		catch (DateTimeParseException ex) {
			// try the other shapes
		}
		try {
			return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
		}
		catch (DateTimeParseException ex) {
			// try a date alone
		}
		try {
			return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}
}
