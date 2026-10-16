package com.jobsearch.joblist.source;

import java.math.BigDecimal;
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
}
