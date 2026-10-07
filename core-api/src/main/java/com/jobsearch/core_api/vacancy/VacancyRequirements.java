package com.jobsearch.core_api.vacancy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Case-insensitive requirement dedupe shared by manual CRUD and LLM import.
 * Keeps the first-seen casing; ORs {@code required} when merging duplicates.
 */
final class VacancyRequirements {

	private static final int MAX_NAME_LENGTH = 255;

	private VacancyRequirements() {
	}

	record Item(String name, boolean required) {
	}

	static List<Item> dedupe(Iterable<Item> items, int maxSize) {
		Map<String, Item> unique = new LinkedHashMap<>();
		for (Item item : items) {
			if (unique.size() >= maxSize) {
				break;
			}
			if (item == null || item.name() == null || item.name().isBlank()) {
				continue;
			}
			String name = item.name().strip();
			if (name.length() > MAX_NAME_LENGTH) {
				name = name.substring(0, MAX_NAME_LENGTH).strip();
			}
			if (name.isBlank()) {
				continue;
			}
			String key = name.toLowerCase(Locale.ROOT);
			Item existing = unique.get(key);
			if (existing == null) {
				unique.put(key, new Item(name, item.required()));
			}
			else if (item.required() && !existing.required()) {
				unique.put(key, new Item(existing.name(), true));
			}
		}
		return new ArrayList<>(unique.values());
	}
}
