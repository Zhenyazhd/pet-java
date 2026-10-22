package com.jobsearch.joblist.rank;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Checks the model's answer against the contract entry by entry. An entry that is valid, asks for a job we sent and is
 * the first for that job is used; every other entry is ignored and its job reported as missing, so one bad entry does not
 * throw away (and pay again for) the good ones. No default scores are invented.
 */
final class RankAnswerParser {

	private static final int MAX_REASON_CHARS = 500;

	private RankAnswerParser() {
	}

	/** @param valid the usable entries; @param missing the expected jobs that have no usable entry */
	record Parsed(List<RankedJob> valid, Set<Long> missing) {
	}

	static Parsed parse(ObjectMapper json, String content, Set<Long> expectedIds) {
		JsonNode results;
		try {
			results = json.readTree(content).path("results");
		}
		catch (JacksonException ex) {
			return new Parsed(List.of(), new LinkedHashSet<>(expectedIds));
		}
		List<RankedJob> valid = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		if (results.isArray()) {
			for (JsonNode item : results) {
				Optional<RankedJob> job = entry(item);
				if (job.isPresent() && expectedIds.contains(job.get().jobId()) && seen.add(job.get().jobId())) {
					valid.add(job.get());
				}
			}
		}
		Set<Long> missing = new LinkedHashSet<>(expectedIds);
		missing.removeAll(seen);
		return new Parsed(valid, missing);
	}

	private static Optional<RankedJob> entry(JsonNode item) {
		OptionalLong id = wholeNumber(item.path("id"));
		OptionalLong score = wholeNumber(item.path("score"));
		String reason = item.path("reason").asString("").strip();
		boolean valid = id.isPresent() && score.isPresent() && score.getAsLong() >= 0 && score.getAsLong() <= 100 && !reason.isEmpty();
		if (!valid) {
			return Optional.empty();
		}
		String shortReason = reason.length() <= MAX_REASON_CHARS ? reason : reason.substring(0, MAX_REASON_CHARS).strip();
		return Optional.of(new RankedJob(id.getAsLong(), (int) score.getAsLong(), shortReason));
	}

	/** A whole number, however the model wrote it: 85, 85.0 or "85" (a score of 85.5 is not whole and is refused). */
	private static OptionalLong wholeNumber(JsonNode value) {
		if (value.isIntegralNumber()) {
			return OptionalLong.of(value.longValue());
		}
		if (value.isFloatingPointNumber() && value.doubleValue() == Math.rint(value.doubleValue())) {
			return OptionalLong.of((long) value.doubleValue());
		}
		if (value.isString() && value.asString().strip().matches("\\d{1,18}")) {
			return OptionalLong.of(Long.parseLong(value.asString().strip()));
		}
		return OptionalLong.empty();
	}
}
