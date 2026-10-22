package com.jobsearch.joblist.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobsearch.joblist.TestData;
import com.jobsearch.joblist.TestProperties;
import com.jobsearch.joblist.job.Job;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ApifyFetcherTest {

	private static final BigDecimal CAP = new BigDecimal("0.30");

	private final ApifyClient apify = mock(ApifyClient.class);
	private final ApifyFetcher fetcher = new ApifyFetcher(apify, TestProperties.defaults());
	private final ObjectMapper json = new ObjectMapper();

	@Test
	void runThatReportsNoCostIsCountedAtTheCap() {
		finishedRun(null);
		items("{\"id\": 1}");

		SourceResult result = fetch(item -> Optional.of(TestData.job(SourceName.WTTJ, "T")));

		assertTrue(result.ok());
		assertEquals(0, CAP.compareTo(result.costUsd()));
	}

	@Test
	void reportedCostIsKept() {
		finishedRun(new BigDecimal("0.07"));
		items("{\"id\": 1}");

		assertEquals(0, new BigDecimal("0.07").compareTo(fetch(item -> Optional.of(TestData.job(SourceName.WTTJ, "T"))).costUsd()));
	}

	@Test
	void failureAfterTheRunStartedAbortsItAndStillCountsTheCap() {
		ApifyClient.Run started = new ApifyClient.Run("r1", "RUNNING", "d1", null);
		when(apify.start(anyString(), any())).thenReturn(started);
		when(apify.awaitFinished(started)).thenThrow(new RestClientException("boom"));

		SourceResult result = fetch(item -> Optional.empty());

		assertFalse(result.ok());
		assertEquals("r1", result.apifyRunId());
		assertEquals(0, CAP.compareTo(result.costUsd()));
		verify(apify).abortQuietly("r1");
	}

	@Test
	void failureToReadTheDatasetOfAFinishedRunCountsItsReportedCostNotTheCap() {
		finishedRun(new BigDecimal("0.07"));
		when(apify.items(anyString(), anyInt())).thenThrow(new RestClientException("dataset gone"));

		SourceResult result = fetch(item -> Optional.empty());

		assertFalse(result.ok());
		assertEquals(0, new BigDecimal("0.07").compareTo(result.costUsd()));
	}

	@Test
	void runCutShortByTheTimeoutCountsTheCapNotTheSnapshotOfItsCost() {
		ApifyClient.Run started = new ApifyClient.Run("r1", "RUNNING", "d1", null);
		when(apify.start(anyString(), any())).thenReturn(started);
		when(apify.awaitFinished(started)).thenReturn(new ApifyClient.Run("r1", "ABORTED", "d1", new BigDecimal("0.01")));

		SourceResult result = fetch(item -> Optional.empty());

		assertFalse(result.ok());
		assertEquals(0, CAP.compareTo(result.costUsd()), "an abort snapshot is taken while the run still bills");
	}

	@Test
	void gatewayErrorOnStartMayHaveCreatedTheRunSoTheCapIsCounted() {
		when(apify.start(anyString(), any())).thenThrow(
				org.springframework.web.client.HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "bad gateway", HttpHeaders.EMPTY, new byte[0], null));

		SourceResult result = fetch(item -> Optional.empty());

		assertFalse(result.ok());
		assertEquals(0, CAP.compareTo(result.costUsd()));
	}

	@Test
	void runThatApifyRefusedToStartCostsNothing() {
		when(apify.start(anyString(), any())).thenThrow(
				HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "no", HttpHeaders.EMPTY, new byte[0], null));

		SourceResult result = fetch(item -> Optional.empty());

		assertFalse(result.ok());
		assertEquals(0, BigDecimal.ZERO.compareTo(result.costUsd()));
	}

	@Test
	void startRequestThatGotNoAnswerMayHaveStartedARunSoTheCapIsCounted() {
		when(apify.start(anyString(), any())).thenThrow(new ResourceAccessException("read timed out"));

		SourceResult result = fetch(item -> Optional.empty());

		assertFalse(result.ok());
		assertEquals(0, CAP.compareTo(result.costUsd()));
	}

	@Test
	void mapperThatThrowsOnOneItemDropsItButKeepsTheRest() {
		finishedRun(new BigDecimal("0.10"));
		items("{\"id\": 1}", "{\"id\": 2}", "{\"id\": 3}", "{\"id\": 4}");

		SourceResult result = fetch(item -> {
			if (item.get("id").intValue() == 2) {
				throw new IllegalStateException("odd item");
			}
			return Optional.of(TestData.job(SourceName.WTTJ, "T"));
		});

		assertTrue(result.ok());
		assertEquals(3, result.jobs().size());
		assertEquals(1, result.dropped());
	}

	@Test
	void sourceWhoseItemsMostlyCannotBeReadFailsWithAHintAboutTheFormat() {
		finishedRun(new BigDecimal("0.10"));
		items("{\"id\": 1}", "{\"id\": 2}", "{\"id\": 3}");

		SourceResult result = fetch(item -> item.get("id").intValue() == 1 ? Optional.of(TestData.job(SourceName.WTTJ, "T")) : Optional.empty());

		assertFalse(result.ok());
		assertTrue(result.error().contains("output format changed"));
		assertEquals(0, new BigDecimal("0.10").compareTo(result.costUsd()));
	}

	private SourceResult fetch(java.util.function.Function<JsonNode, Optional<Job>> mapper) {
		return fetcher.fetch(SourceName.WTTJ, "owner/actor", json.createObjectNode(), 10, mapper);
	}

	private void finishedRun(BigDecimal cost) {
		ApifyClient.Run started = new ApifyClient.Run("r1", "RUNNING", "d1", null);
		ApifyClient.Run finished = new ApifyClient.Run("r1", "SUCCEEDED", "d1", cost);
		when(apify.start(anyString(), any())).thenReturn(started);
		when(apify.awaitFinished(started)).thenReturn(finished);
	}

	private void items(String... jsonItems) {
		List<JsonNode> nodes = java.util.Arrays.stream(jsonItems).map(json::readTree).toList();
		when(apify.items(anyString(), anyInt())).thenReturn(nodes);
	}
}
