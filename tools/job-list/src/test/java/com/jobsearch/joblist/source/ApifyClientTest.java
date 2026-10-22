package com.jobsearch.joblist.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jobsearch.joblist.TestProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ApifyClientTest {

	private static final String RUN = "{\"data\": {\"id\": \"r1\", \"status\": \"%s\", \"defaultDatasetId\": \"d1\", \"usageTotalUsd\": 0.12}}";

	private final ObjectMapper json = new ObjectMapper();

	@Test
	void startsWithACostCapPollsUntilFinishedAndReadsPagedItems() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://apify.test");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ApifyClient client = new ApifyClient(builder.build(), TestProperties.defaults());
		server.expect(requestTo("http://apify.test/v2/acts/dltik~hellowork-scraper/runs?maxTotalChargeUsd=0.30&timeout=5"))
				.andExpect(method(HttpMethod.POST)).andRespond(withSuccess(RUN.formatted("RUNNING"), MediaType.APPLICATION_JSON));
		server.expect(requestTo("http://apify.test/v2/actor-runs/r1")).andRespond(withSuccess(RUN.formatted("RUNNING"), MediaType.APPLICATION_JSON));
		server.expect(requestTo("http://apify.test/v2/actor-runs/r1")).andRespond(withSuccess(RUN.formatted("SUCCEEDED"), MediaType.APPLICATION_JSON));
		server.expect(requestTo("http://apify.test/v2/datasets/d1/items?clean=true&format=json&offset=0&limit=100"))
				.andRespond(withSuccess(items(100), MediaType.APPLICATION_JSON));
		server.expect(requestTo("http://apify.test/v2/datasets/d1/items?clean=true&format=json&offset=100&limit=20"))
				.andRespond(withSuccess(items(7), MediaType.APPLICATION_JSON));

		ApifyClient.Run started = client.start("dltik/hellowork-scraper", json.createObjectNode());
		ApifyClient.Run finished = client.awaitFinished(started);
		List<JsonNode> items = client.items(finished.datasetId(), 120);

		assertTrue(finished.succeeded());
		assertEquals(0, new java.math.BigDecimal("0.12").compareTo(finished.costUsd()));
		assertEquals(107, items.size());
		server.verify();
	}

	@Test
	void oneFailedPollOrDatasetReadIsRetriedInsteadOfLosingAPaidRun() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://apify.test");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ApifyClient client = new ApifyClient(builder.build(), TestProperties.defaults());
		server.expect(requestTo("http://apify.test/v2/actor-runs/r1")).andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());
		server.expect(requestTo("http://apify.test/v2/actor-runs/r1")).andRespond(withSuccess(RUN.formatted("SUCCEEDED"), MediaType.APPLICATION_JSON));
		server.expect(requestTo("http://apify.test/v2/datasets/d1/items?clean=true&format=json&offset=0&limit=100"))
				.andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(HttpStatus.BAD_GATEWAY));
		server.expect(requestTo("http://apify.test/v2/datasets/d1/items?clean=true&format=json&offset=0&limit=100"))
				.andRespond(withSuccess(items(3), MediaType.APPLICATION_JSON));

		ApifyClient.Run finished = client.awaitFinished(new ApifyClient.Run("r1", "RUNNING", "d1", null));
		List<JsonNode> items = client.items("d1", 100);

		assertTrue(finished.succeeded());
		assertEquals(3, items.size());
		server.verify();
	}

	@Test
	void aPollThatKeepsFailingGivesUpAfterThreeTries() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://apify.test");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ApifyClient client = new ApifyClient(builder.build(), TestProperties.defaults());
		for (int i = 0; i < 3; i++) {
			server.expect(requestTo("http://apify.test/v2/actor-runs/r1"))
					.andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());
		}

		org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.client.HttpServerErrorException.class,
				() -> client.awaitFinished(new ApifyClient.Run("r1", "RUNNING", "d1", null)));

		server.verify();
	}

	@Test
	void aClientErrorIsNotRetried() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://apify.test");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ApifyClient client = new ApifyClient(builder.build(), TestProperties.defaults());
		server.expect(requestTo("http://apify.test/v2/actor-runs/r1"))
				.andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(HttpStatus.UNAUTHORIZED));

		org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.client.HttpClientErrorException.class,
				() -> client.awaitFinished(new ApifyClient.Run("r1", "RUNNING", "d1", null)));

		server.verify();
	}

	@Test
	void runThatOutlivesTheTimeoutIsAborted() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://apify.test");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
		ApifyClient client = new ApifyClient(builder.build(), TestProperties.withRunTimeout(Duration.ofMillis(30)));
		server.expect(requestTo("http://apify.test/v2/actor-runs/r1/abort")).andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(RUN.formatted("ABORTED"), MediaType.APPLICATION_JSON));
		server.expect(org.springframework.test.web.client.ExpectedCount.manyTimes(), requestTo("http://apify.test/v2/actor-runs/r1"))
				.andRespond(withSuccess(RUN.formatted("RUNNING"), MediaType.APPLICATION_JSON));

		ApifyClient.Run result = client.awaitFinished(new ApifyClient.Run("r1", "RUNNING", "d1", null));

		assertEquals("ABORTED", result.status());
		server.verify();
	}

	private String items(int count) {
		return "[" + String.join(",", java.util.Collections.nCopies(count, "{\"id\": \"x\"}")) + "]";
	}
}
