package com.jobsearch.core_api.jobsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.jobsearch.core_api.common.BadRequestException;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.common.ServiceUnavailableException;
import com.jobsearch.core_api.common.TooManyRequestsException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class JobListClientTest {

	private MockRestServiceServer server;
	private JobListClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://job-list");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new JobListClient(builder.build(), builder.build(), JsonMapper.builder().build());
	}

	private void answer(HttpStatus status, String body) {
		server.expect(requestTo("http://job-list/v1/profiles/7/runs"))
				.andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
	}

	@Test
	void notFoundKeepsItsMessage() {
		answer(HttpStatus.NOT_FOUND, "{\"status\":404,\"error\":\"Not Found\",\"message\":\"Profile not found: 7\"}");

		assertEquals("Profile not found: 7", assertThrows(NotFoundException.class, () -> client.startRun(7)).getMessage());
	}

	@Test
	void tooManyRequestsKeepsItsMessageAndRetryAfter() {
		server.expect(requestTo("http://job-list/v1/profiles/7/runs"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "60")
						.contentType(MediaType.APPLICATION_JSON)
						.body("{\"status\":429,\"error\":\"x\",\"message\":\"Daily run limit reached\"}"));

		TooManyRequestsException ex = assertThrows(TooManyRequestsException.class, () -> client.startRun(7));

		assertEquals("Daily run limit reached", ex.getMessage());
		assertEquals(60, ex.getRetryAfter().toSeconds());
	}

	@Test
	void otherClientErrorsAreBadRequestsWithTheirMessage() {
		answer(HttpStatus.BAD_REQUEST, "{\"status\":400,\"error\":\"Bad Request\",\"message\":\"regions: bad code\"}");

		assertEquals("regions: bad code", assertThrows(BadRequestException.class, () -> client.startRun(7)).getMessage());
	}

	@Test
	void aRejectedTokenServerErrorsAndNetworkFailuresAreUnavailable() {
		answer(HttpStatus.UNAUTHORIZED, "{\"message\":\"A valid bearer token is required\"}");
		answer(HttpStatus.INTERNAL_SERVER_ERROR, "<html>oops</html>");
		server.expect(requestTo("http://job-list/v1/profiles/7/runs")).andRespond(withException(new IOException("refused")));

		for (int i = 0; i < 3; i++) {
			ServiceUnavailableException ex = assertThrows(ServiceUnavailableException.class, () -> client.startRun(7));
			assertEquals("Job search is temporarily unavailable", ex.getMessage());
		}
	}

	private void answerRank(HttpStatus status, String body) {
		server.expect(requestTo("http://job-list/v1/profiles/7/runs?mode=rank"))
				.andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
	}

	@Test
	void startRankRunReadsTheRunWithItsMode() {
		answerRank(HttpStatus.ACCEPTED, "{\"id\":5,\"status\":\"QUEUED\",\"mode\":\"RANK_ONLY\",\"fetched\":0,\"newJobs\":0,\"ranked\":0,\"costUsd\":0,\"error\":null,\"sources\":[],\"x\":1}");

		var run = client.startRankRun(7);

		assertEquals("RANK_ONLY", run.mode());
		assertEquals(0, run.sources().size());
	}

	@Test
	void aRunWithoutModeIsFull() {
		answer(HttpStatus.ACCEPTED, "{\"id\":5,\"status\":\"QUEUED\",\"fetched\":0,\"newJobs\":0,\"ranked\":0,\"costUsd\":0,\"error\":null,\"sources\":[]}");

		assertEquals("FULL", client.startRun(7).mode());
	}

	@Test
	void aRankRunAnsweredWithoutRankModeIsUnavailable() {
		answerRank(HttpStatus.ACCEPTED, "{\"id\":5,\"status\":\"QUEUED\",\"fetched\":0,\"newJobs\":0,\"ranked\":0,\"costUsd\":0,\"error\":null,\"sources\":[]}");
		answerRank(HttpStatus.ACCEPTED, "{\"id\":5,\"status\":\"QUEUED\",\"mode\":\"FULL\",\"fetched\":0,\"newJobs\":0,\"ranked\":0,\"costUsd\":0,\"error\":null,\"sources\":[]}");

		assertThrows(ServiceUnavailableException.class, () -> client.startRankRun(7));
		assertThrows(ServiceUnavailableException.class, () -> client.startRankRun(7));
	}

	@Test
	void runConflictKeepsJobListsMessage() {
		answer(HttpStatus.CONFLICT, "{\"message\":\"A ranking run is already in progress for this profile\"}");

		assertEquals("A ranking run is already in progress for this profile",
				assertThrows(ConflictException.class, () -> client.startRun(7)).getMessage());
	}

	@Test
	void rankRunConflictKeepsJobListsMessage() {
		answerRank(HttpStatus.CONFLICT, "{\"status\":409,\"error\":\"Conflict\",\"message\":\"Nothing to rank\"}");

		assertEquals("Nothing to rank", assertThrows(ConflictException.class, () -> client.startRankRun(7)).getMessage());
	}

	@Test
	void rankRunMapsTheOtherErrorsLikeARun() {
		answerRank(HttpStatus.NOT_FOUND, "{\"message\":\"Profile not found: 7\"}");
		server.expect(requestTo("http://job-list/v1/profiles/7/runs?mode=rank"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "30")
						.contentType(MediaType.APPLICATION_JSON).body("{\"message\":\"Daily rank run limit reached\"}"));
		answerRank(HttpStatus.BAD_GATEWAY, "oops");
		server.expect(requestTo("http://job-list/v1/profiles/7/runs?mode=rank")).andRespond(withException(new IOException("refused")));
		server.expect(requestTo("http://job-list/v1/profiles/7/runs?mode=rank")).andRespond(withStatus(HttpStatus.ACCEPTED));

		assertThrows(NotFoundException.class, () -> client.startRankRun(7));
		assertEquals(30, assertThrows(TooManyRequestsException.class, () -> client.startRankRun(7)).getRetryAfter().toSeconds());
		for (int i = 0; i < 3; i++) {
			assertThrows(ServiceUnavailableException.class, () -> client.startRankRun(7));
		}
	}

	@Test
	void unrankedReadsTheCountAndMapsFailures() {
		server.expect(requestTo("http://job-list/v1/profiles/7/unranked"))
				.andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("{\"count\":12}"));
		server.expect(requestTo("http://job-list/v1/profiles/7/unranked")).andRespond(withStatus(HttpStatus.OK));
		server.expect(requestTo("http://job-list/v1/profiles/7/unranked")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
		server.expect(requestTo("http://job-list/v1/profiles/7/unranked"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body("{\"message\":\"Profile not found: 7\"}"));

		assertEquals(12, client.unranked(7));
		assertThrows(ServiceUnavailableException.class, () -> client.unranked(7));
		assertThrows(ServiceUnavailableException.class, () -> client.unranked(7));
		assertThrows(NotFoundException.class, () -> client.unranked(7));
	}

	@Test
	void anEmptyBodyIsUnavailable() {
		server.expect(requestTo("http://job-list/v1/profiles/7/runs")).andRespond(withStatus(HttpStatus.ACCEPTED));

		assertThrows(ServiceUnavailableException.class, () -> client.startRun(7));
	}
}
