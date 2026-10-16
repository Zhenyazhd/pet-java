package com.jobsearch.joblist.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RankAnswerParserTest {

	private final ObjectMapper json = new ObjectMapper();

	@Test
	void validAnswerForExactlyTheExpectedJobsIsAccepted() {
		RankAnswerParser.Parsed answer = parse("{\"results\": [{\"id\": 1, \"score\": 80, \"reason\": \" Good fit. \"}, {\"id\": 2, \"score\": 0, \"reason\": \"No.\"}]}", Set.of(1L, 2L));

		assertEquals(List.of(new RankedJob(1, 80, "Good fit."), new RankedJob(2, 0, "No.")), answer.valid());
		assertTrue(answer.missing().isEmpty());
	}

	@Test
	void theValidEntriesOfAPartlyBadAnswerAreKeptAndTheRestIsReportedMissing() {
		RankAnswerParser.Parsed answer = parse("""
				{"results": [
				  {"id": 1, "score": 80, "reason": "ok"},
				  {"id": 2, "score": 180, "reason": "score out of range"},
				  {"id": 3, "score": 70.5, "reason": "score not whole"},
				  {"id": 4, "score": 60, "reason": "  "},
				  {"id": 9, "score": 60, "reason": "not asked for"},
				  {"id": 5, "score": 40, "reason": "fine"}]}""", Set.of(1L, 2L, 3L, 4L, 5L, 6L));

		assertEquals(List.of(new RankedJob(1, 80, "ok"), new RankedJob(5, 40, "fine")), answer.valid());
		assertEquals(Set.of(2L, 3L, 4L, 6L), answer.missing());
	}

	@Test
	void aDuplicateIdKeepsTheFirstEntry() {
		RankAnswerParser.Parsed answer = parse("{\"results\": [{\"id\": 1, \"score\": 80, \"reason\": \"x\"}, {\"id\": 1, \"score\": 10, \"reason\": \"y\"}]}", Set.of(1L, 2L));

		assertEquals(List.of(new RankedJob(1, 80, "x")), answer.valid());
		assertEquals(Set.of(2L), answer.missing());
	}

	@Test
	void aWholeNumberIsAcceptedHoweverTheModelWroteIt() {
		RankAnswerParser.Parsed answer = parse("""
				{"results": [{"id": "1", "score": "85", "reason": "string numbers"},
				             {"id": 2.0, "score": 85.0, "reason": "floats"},
				             {"id": 3, "score": 85.5, "reason": "not whole"}]}""", Set.of(1L, 2L, 3L));

		assertEquals(List.of(new RankedJob(1, 85, "string numbers"), new RankedJob(2, 85, "floats")), answer.valid());
		assertEquals(Set.of(3L), answer.missing());
	}

	@Test
	void nothingUsableMeansEveryJobIsMissing() {
		assertEquals(Set.of(1L), parse("not json", Set.of(1L)).missing());
		assertEquals(Set.of(1L), parse("{\"results\": []}", Set.of(1L)).missing());
		assertEquals(Set.of(1L), parse("{\"results\": \"none\"}", Set.of(1L)).missing());
	}

	@Test
	void longReasonIsShortened() {
		String reason = "x".repeat(900);

		RankedJob job = parse("{\"results\": [{\"id\": 1, \"score\": 5, \"reason\": \"" + reason + "\"}]}", Set.of(1L)).valid().getFirst();

		assertEquals(500, job.reason().length());
	}

	private RankAnswerParser.Parsed parse(String content, Set<Long> ids) {
		return RankAnswerParser.parse(json, content, ids);
	}
}
