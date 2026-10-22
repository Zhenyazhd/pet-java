package com.jobsearch.core_api.jobsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.auth.AppUserRepository;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** /api/job-search as a signed-in browser sees it, against a stubbed job-list that does not check owners (like the real one). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class JobSearchHttpTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final JobListStub STUB = startStub();
	private static final String PROFILE = "{\"expectations\":\"Backend, remote\",\"roles\":[\"Java developer\"],"
			+ "\"regions\":[{\"country\":\"fr\",\"city\":\" Paris \"},{\"country\":\"DE\",\"city\":null}],\"minScore\":60}";

	@Value("${local.server.port}")
	private int port;
	@Autowired
	private AppUserRepository users;
	@Autowired
	private PasswordEncoder encoder;

	@Autowired
	private com.jobsearch.core_api.common.FixedWindowRateLimiter rateLimiter;

	private Browser alice;
	private Browser bob;

	private static JobListStub startStub() {
		try {
			return new JobListStub();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void jobList(DynamicPropertyRegistry registry) {
		registry.add("app.job-list.base-url", STUB::baseUrl);
		registry.add("app.job-list.token", () -> JobListStub.TOKEN);
	}

	@AfterAll
	static void stopStub() {
		STUB.stop();
	}

	@BeforeEach
	void signIn() throws Exception {
		rateLimiter.clear();
		STUB.reset();
		alice = new Browser(port, users, encoder, true);
		bob = new Browser(port, users, encoder, true);
	}

	@Test
	void unauthenticatedIsRefused() throws Exception {
		Browser anonymous = new Browser(port, users, encoder, false);

		assertEquals(401, anonymous.send("GET", "/api/job-search", null).statusCode());
		assertEquals(401, anonymous.send("PUT", "/api/job-search/profile", PROFILE).statusCode());
		assertEquals(401, anonymous.send("POST", "/api/job-search/runs", null).statusCode());
		assertEquals(401, anonymous.send("GET", "/api/job-search/matches", null).statusCode());
		assertEquals(401, anonymous.send("POST", "/api/job-search/matches/1/seen", null).statusCode());
		assertTrue(STUB.requests.isEmpty());
	}

	@Test
	void postWithoutCsrfTokenIsRefused() throws Exception {
		java.net.http.HttpClient bare = java.net.http.HttpClient.newHttpClient();
		HttpResponse<String> response = bare.send(java.net.http.HttpRequest.newBuilder(
				java.net.URI.create("http://localhost:" + port + "/api/job-search/runs"))
				.POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());

		assertTrue(response.statusCode() == 401 || response.statusCode() == 403, response.body());
	}

	@Test
	void emptyStateWhenThereIsNoProfile() throws Exception {
		JsonNode state = json(alice.send("GET", "/api/job-search", null));

		assertTrue(state.path("configured").asBoolean());
		assertTrue(state.path("profile").isNull());
		assertTrue(state.path("latestRun").isNull());
	}

	@Test
	void putCreatesThenUpdatesTheSingleProfile() throws Exception {
		HttpResponse<String> created = alice.send("PUT", "/api/job-search/profile", PROFILE);
		assertEquals(200, created.statusCode(), created.body());
		JsonNode profile = json(created);
		assertEquals("Search", profile.path("name").asString());
		assertEquals("FR", profile.at("/regions/0/country").asString());
		assertEquals("Paris", profile.at("/regions/0/city").asString());
		assertTrue(profile.at("/regions/1/city").isNull());
		assertEquals(60, profile.path("minScore").asInt());
		assertFalse(profile.has("ownerRef"));

		String update = "{\"expectations\":\"Other\",\"roles\":[\"Kotlin developer\"]}";
		JsonNode updated = json(alice.send("PUT", "/api/job-search/profile", update));

		assertEquals(profile.path("id").asLong(), updated.path("id").asLong());
		assertEquals("Other", updated.path("expectations").asString());
		assertEquals(0, updated.path("minScore").asInt());
		assertEquals(1, STUB.profiles.size());
		assertEquals(String.valueOf(alice.userId), STUB.profiles.getFirst().ownerRef());
		assertEquals("Bearer " + JobListStub.TOKEN, STUB.lastToken);
	}

	@Test
	void usersNeverSeeOrChangeEachOthersProfile() throws Exception {
		long aliceProfile = json(alice.send("PUT", "/api/job-search/profile", PROFILE)).path("id").asLong();

		assertTrue(json(bob.send("GET", "/api/job-search", null)).path("profile").isNull());
		long bobProfile = json(bob.send("PUT", "/api/job-search/profile",
				"{\"expectations\":\"Mine\",\"roles\":[\"QA\"]}")).path("id").asLong();

		assertTrue(aliceProfile != bobProfile);
		assertEquals(2, STUB.profiles.size());
		assertEquals("Backend, remote", json(alice.send("GET", "/api/job-search", null)).at("/profile/expectations").asString());
		assertEquals("Mine", json(bob.send("GET", "/api/job-search", null)).at("/profile/expectations").asString());
	}

	@Test
	void validationErrors() throws Exception {
		String[] bad = {
				"{\"expectations\":\"\",\"roles\":[\"a\"]}",
				"{\"expectations\":\"" + "x".repeat(5001) + "\",\"roles\":[\"a\"]}",
				"{\"expectations\":\"x\",\"roles\":[]}",
				"{\"expectations\":\"x\"}",
				"{\"expectations\":\"x\",\"roles\":[\" \"]}",
				"{\"expectations\":\"x\",\"roles\":[\"" + "r".repeat(101) + "\"]}",
				"{\"expectations\":\"x\",\"roles\":[" + "\"r\",".repeat(30) + "\"r\"]}",
				"{\"expectations\":\"x\",\"roles\":[\"r\"],\"regions\":[{\"country\":\"FRA\"}]}",
				"{\"expectations\":\"x\",\"roles\":[\"r\"],\"regions\":[{\"country\":\"F1\"}]}",
				"{\"expectations\":\"x\",\"roles\":[\"r\"],\"regions\":[{\"country\":\"FR\",\"city\":\"" + "c".repeat(101) + "\"}]}",
				"{\"expectations\":\"x\",\"roles\":[\"r\"],\"minScore\":101}",
				"{\"expectations\":\"x\",\"roles\":[\"r\"],\"minScore\":-1}",
		};
		for (String body : bad) {
			HttpResponse<String> response = alice.send("PUT", "/api/job-search/profile", body);
			assertEquals(400, response.statusCode(), body + " -> " + response.body());
		}
		assertTrue(STUB.requests.isEmpty());
	}

	@Test
	void tooManyRegionsIsRefused() throws Exception {
		String regions = String.join(",", java.util.Collections.nCopies(21, "{\"country\":\"FR\"}"));

		assertEquals(400, alice.send("PUT", "/api/job-search/profile",
				"{\"expectations\":\"x\",\"roles\":[\"r\"],\"regions\":[" + regions + "]}").statusCode());
	}

	@Test
	void runNeedsAProfile() throws Exception {
		HttpResponse<String> response = alice.send("POST", "/api/job-search/runs", null);

		assertEquals(409, response.statusCode(), response.body());
		assertTrue(STUB.requests.stream().noneMatch(r -> r.startsWith("POST")));
	}

	@Test
	void runStartsForTheUsersOwnProfileAndLatestRunIsReturned() throws Exception {
		alice.send("PUT", "/api/job-search/profile", PROFILE);
		bob.send("PUT", "/api/job-search/profile", "{\"expectations\":\"Mine\",\"roles\":[\"QA\"]}");
		long aliceProfile = STUB.profiles.get(0).id();

		HttpResponse<String> first = alice.send("POST", "/api/job-search/runs", null);
		HttpResponse<String> second = alice.send("POST", "/api/job-search/runs", null);

		assertEquals(202, first.statusCode(), first.body());
		JsonNode run = json(second);
		assertEquals("QUEUED", run.path("status").asString());
		assertFalse(second.body().contains("apifyRunId"));
		assertFalse(second.body().contains("secret-apify-id"));
		assertEquals(2, STUB.requests.stream().filter(r -> r.equals("POST /v1/profiles/" + aliceProfile + "/runs")).count());
		assertEquals(run.path("id").asLong(), json(alice.send("GET", "/api/job-search", null)).at("/latestRun/id").asLong());
		assertTrue(json(bob.send("GET", "/api/job-search", null)).path("latestRun").isNull());
		assertEquals("LINKEDIN", json(alice.send("GET", "/api/job-search", null)).at("/latestRun/sources/0/source").asString());
	}

	@Test
	void runLimitIs429WithJobListsMessage() throws Exception {
		alice.send("PUT", "/api/job-search/profile", PROFILE);
		STUB.runStatus = 429;
		STUB.runBody = "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Daily run limit of 3 reached\"}";

		HttpResponse<String> response = alice.send("POST", "/api/job-search/runs", null);

		assertEquals(429, response.statusCode());
		assertEquals("Daily run limit of 3 reached", json(response).path("message").asString());
	}

	@Test
	void jobListDownIs503() throws Exception {
		alice.send("PUT", "/api/job-search/profile", PROFILE);
		STUB.runStatus = 500;
		STUB.runBody = "{\"status\":500,\"error\":\"x\",\"message\":\"boom internal\"}";

		HttpResponse<String> response = alice.send("POST", "/api/job-search/runs", null);

		assertEquals(503, response.statusCode());
		assertFalse(response.body().contains("boom internal"));
	}

	@Test
	void matchesAreTheUsersOwnAndParamsPassThrough() throws Exception {
		alice.send("PUT", "/api/job-search/profile", PROFILE);
		bob.send("PUT", "/api/job-search/profile", "{\"expectations\":\"Mine\",\"roles\":[\"QA\"]}");
		long aliceProfile = STUB.profiles.get(0).id();
		long bobProfile = STUB.profiles.get(1).id();
		for (int i = 0; i < 5; i++) {
			STUB.addMatch(aliceProfile, 90 - i, false);
		}
		JobListStub.Match bobsMatch = STUB.addMatch(bobProfile, 99, false);

		JsonNode page = json(alice.send("GET", "/api/job-search/matches?limit=2&minScore=50", null));

		assertEquals(2, page.path("items").size());
		assertEquals("Java dev", page.at("/items/0/job/title").asString());
		assertEquals("c2", page.path("nextCursor").asString());
		assertTrue(STUB.requests.contains("GET /v1/profiles/" + aliceProfile + "/matches?minScore=50&limit=2"));

		JsonNode next = json(alice.send("GET", "/api/job-search/matches?limit=2&cursor=" + page.path("nextCursor").asString(), null));
		assertEquals(2, next.path("items").size());
		assertTrue(STUB.requests.contains("GET /v1/profiles/" + aliceProfile + "/matches?limit=2&cursor=c2"));
		for (JsonNode item : page.path("items")) {
			assertTrue(item.path("id").asLong() != bobsMatch.id());
		}
	}

	@Test
	void aCursorCannotSwitchProfiles() throws Exception {
		alice.send("PUT", "/api/job-search/profile", PROFILE);
		long aliceProfile = STUB.profiles.get(0).id();

		HttpResponse<String> response = alice.send("GET", "/api/job-search/matches?cursor="
				+ java.net.URLEncoder.encode("1&x=2/../../profiles/999", java.nio.charset.StandardCharsets.UTF_8), null);

		assertEquals(400, response.statusCode());
		assertTrue(STUB.requests.stream().filter(r -> r.contains("/matches")).allMatch(
				r -> r.startsWith("GET /v1/profiles/" + aliceProfile + "/matches?limit=20&cursor=1%26x%3D2%2F")),
				STUB.requests.toString());
		assertTrue(STUB.requests.stream().noneMatch(r -> r.contains("999") && !r.contains("cursor=")));
	}

	@Test
	void limitIsClampedAndDefaultsTo20() throws Exception {
		alice.send("PUT", "/api/job-search/profile", PROFILE);
		long id = STUB.profiles.getFirst().id();

		alice.send("GET", "/api/job-search/matches", null);
		alice.send("GET", "/api/job-search/matches?limit=1000", null);
		alice.send("GET", "/api/job-search/matches?limit=0&delivered=true&minScore=500", null);

		String base = "GET /v1/profiles/" + id + "/matches?";
		assertTrue(STUB.requests.contains(base + "limit=20"), STUB.requests.toString());
		assertTrue(STUB.requests.contains(base + "limit=50"), STUB.requests.toString());
		assertTrue(STUB.requests.contains(base + "minScore=100&delivered=true&limit=1"), STUB.requests.toString());
	}

	@Test
	void matchesWithoutAProfileAreEmpty() throws Exception {
		JsonNode page = json(alice.send("GET", "/api/job-search/matches", null));

		assertEquals(0, page.path("items").size());
		assertNull(page.path("nextCursor").isNull() ? null : "x");
	}

	@Test
	void seenWorksForOwnMatchesOnly() throws Exception {
		alice.send("PUT", "/api/job-search/profile", PROFILE);
		bob.send("PUT", "/api/job-search/profile", "{\"expectations\":\"Mine\",\"roles\":[\"QA\"]}");
		long aliceProfile = STUB.profiles.get(0).id();
		long bobProfile = STUB.profiles.get(1).id();
		// more than one page of her own matches, so the walk has to follow the cursor
		for (int i = 0; i < 120; i++) {
			STUB.addMatch(aliceProfile, 80, false);
		}
		JobListStub.Match last = STUB.matches.getLast();
		JobListStub.Match seenAlready = STUB.addMatch(aliceProfile, 70, true);
		JobListStub.Match bobs = STUB.addMatch(bobProfile, 95, false);

		assertEquals(204, alice.send("POST", "/api/job-search/matches/" + last.id() + "/seen", null).statusCode());
		assertEquals(204, alice.send("POST", "/api/job-search/matches/" + seenAlready.id() + "/seen", null).statusCode());
		assertEquals(java.util.List.of(String.valueOf(last.id()), String.valueOf(seenAlready.id())), STUB.acked);

		assertEquals(404, alice.send("POST", "/api/job-search/matches/" + bobs.id() + "/seen", null).statusCode());
		assertEquals(404, alice.send("POST", "/api/job-search/matches/987654/seen", null).statusCode());
		assertEquals(404, bob.send("POST", "/api/job-search/matches/" + last.id() + "/seen", null).statusCode());
		assertEquals(2, STUB.acked.size());
		assertEquals(204, bob.send("POST", "/api/job-search/matches/" + bobs.id() + "/seen", null).statusCode());
	}

	@Test
	void theWalkStopsAtTheCap() throws Exception {
		alice.send("PUT", "/api/job-search/profile", PROFILE);
		long profile = STUB.profiles.getFirst().id();
		for (int i = 0; i < 510; i++) {
			STUB.addMatch(profile, 80, false);
		}
		JobListStub.Match inReach = STUB.matches.get(499);
		JobListStub.Match tooOld = STUB.matches.get(509);

		assertEquals(204, alice.send("POST", "/api/job-search/matches/" + inReach.id() + "/seen", null).statusCode());
		assertEquals(404, alice.send("POST", "/api/job-search/matches/" + tooOld.id() + "/seen", null).statusCode());
		assertEquals(1, STUB.acked.size());
	}

	@Test
	void aLooseOwnerFilterInJobListDoesNotLeakForeignProfiles() throws Exception {
		bob.send("PUT", "/api/job-search/profile", "{\"expectations\":\"Mine\",\"roles\":[\"QA\"]}");
		STUB.ignoreOwnerRef = true;

		assertTrue(json(alice.send("GET", "/api/job-search", null)).path("profile").isNull());
		assertEquals(409, alice.send("POST", "/api/job-search/runs", null).statusCode());
		assertEquals(0, json(alice.send("GET", "/api/job-search/matches", null)).path("items").size());
		JsonNode created = json(alice.send("PUT", "/api/job-search/profile", PROFILE));
		assertTrue(created.path("id").asLong() != STUB.profiles.getFirst().id());
		assertEquals("Mine", STUB.profiles.getFirst().body().path("expectations").asString());
	}

	@Test
	void aRacingFirstPutUpdatesTheProfileTheOtherRequestCreated() throws Exception {
		alice.send("PUT", "/api/job-search/profile", PROFILE);
		STUB.hideProfileLists = 1;

		HttpResponse<String> response = alice.send("PUT", "/api/job-search/profile",
				"{\"expectations\":\"Second\",\"roles\":[\"r\"]}");

		assertEquals(200, response.statusCode(), response.body());
		assertEquals("Second", json(response).path("expectations").asString());
		assertEquals(1, STUB.profiles.size());
	}

	@Test
	void seenWithoutAProfileIs404() throws Exception {
		assertEquals(404, alice.send("POST", "/api/job-search/matches/5/seen", null).statusCode());
		assertTrue(STUB.acked.isEmpty());
	}

	@Test
	void unrankedIsTheUsersOwnCountAndZeroWithoutAProfile() throws Exception {
		assertEquals(0, json(alice.send("GET", "/api/job-search", null)).path("unranked").asInt(-1));
		alice.send("PUT", "/api/job-search/profile", PROFILE);
		bob.send("PUT", "/api/job-search/profile", "{\"expectations\":\"Mine\",\"roles\":[\"QA\"]}");
		STUB.unranked.put(STUB.profiles.get(0).id(), 7);
		STUB.unranked.put(STUB.profiles.get(1).id(), 3);

		assertEquals(7, json(alice.send("GET", "/api/job-search", null)).path("unranked").asInt(-1));
		assertEquals(3, json(bob.send("GET", "/api/job-search", null)).path("unranked").asInt(-1));
	}

	@Test
	void aFailingUnrankedCountIsZeroNotAnError() throws Exception {
		alice.send("PUT", "/api/job-search/profile", PROFILE);
		STUB.unranked.put(STUB.profiles.get(0).id(), 7);
		STUB.unrankedStatus = 500;

		HttpResponse<String> response = alice.send("GET", "/api/job-search", null);

		assertEquals(200, response.statusCode(), response.body());
		assertTrue(json(response).path("unranked").isNull());
		assertEquals("Search", json(response).at("/profile/name").asString());
	}

	@Test
	void rankRunNeedsAProfile() throws Exception {
		HttpResponse<String> response = alice.send("POST", "/api/job-search/runs/rank", null);

		assertEquals(409, response.statusCode(), response.body());
		assertEquals("Save a search profile first", json(response).path("message").asString());
		assertTrue(STUB.requests.stream().noneMatch(r -> r.startsWith("POST")));
	}

	@Test
	void rankRunStartsForTheUsersOwnProfileOnly() throws Exception {
		alice.send("PUT", "/api/job-search/profile", PROFILE);
		bob.send("PUT", "/api/job-search/profile", "{\"expectations\":\"Mine\",\"roles\":[\"QA\"]}");
		long aliceProfile = STUB.profiles.get(0).id();
		long bobProfile = STUB.profiles.get(1).id();
		STUB.unranked.put(aliceProfile, 4);
		STUB.unranked.put(bobProfile, 4);

		HttpResponse<String> response = alice.send("POST", "/api/job-search/runs/rank", null);

		assertEquals(202, response.statusCode(), response.body());
		JsonNode run = json(response);
		assertEquals("RANK_ONLY", run.path("mode").asString());
		assertEquals(0, run.path("sources").size());
		assertFalse(response.body().contains("apifyRunId"));
		assertTrue(STUB.requests.contains("POST /v1/profiles/" + aliceProfile + "/runs?mode=rank"));
		assertTrue(STUB.requests.stream().noneMatch(r -> r.startsWith("POST /v1/profiles/" + bobProfile)));
		assertEquals(run.path("id").asLong(), json(alice.send("GET", "/api/job-search", null)).at("/latestRun/id").asLong());
		assertTrue(json(bob.send("GET", "/api/job-search", null)).path("latestRun").isNull());
	}

	@Test
	void nothingToRankIsPassedThroughAs409() throws Exception {
		alice.send("PUT", "/api/job-search/profile", PROFILE);

		HttpResponse<String> response = alice.send("POST", "/api/job-search/runs/rank", null);

		assertEquals(409, response.statusCode(), response.body());
		assertEquals("Nothing to rank", json(response).path("message").asString());
	}

	@Test
	void rankRunLimitIs429AndJobListDownIs503() throws Exception {
		alice.send("PUT", "/api/job-search/profile", PROFILE);
		STUB.unranked.put(STUB.profiles.get(0).id(), 2);
		STUB.runStatus = 429;
		STUB.runBody = "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Daily rank run limit of 20 reached\"}";

		HttpResponse<String> limited = alice.send("POST", "/api/job-search/runs/rank", null);
		assertEquals(429, limited.statusCode());
		assertEquals("Daily rank run limit of 20 reached", json(limited).path("message").asString());

		STUB.runStatus = 500;
		STUB.runBody = "{\"status\":500,\"error\":\"x\",\"message\":\"boom internal\"}";
		HttpResponse<String> down = alice.send("POST", "/api/job-search/runs/rank", null);
		assertEquals(503, down.statusCode());
		assertFalse(down.body().contains("boom internal"));
	}

	@Test
	void anActiveRunOfTheOtherModeIs409WithJobListsMessageAndTheSameModeReturnsIt() throws Exception {
		alice.send("PUT", "/api/job-search/profile", PROFILE);
		STUB.unranked.put(STUB.profiles.get(0).id(), 2);
		STUB.dedupe = true;

		JsonNode full = json(alice.send("POST", "/api/job-search/runs", null));
		HttpResponse<String> rank = alice.send("POST", "/api/job-search/runs/rank", null);
		assertEquals(409, rank.statusCode(), rank.body());
		assertEquals("A full run is already in progress for this profile", json(rank).path("message").asString());
		assertEquals(full.path("id").asLong(), json(alice.send("POST", "/api/job-search/runs", null)).path("id").asLong());

		STUB.reset();
		alice.send("PUT", "/api/job-search/profile", PROFILE);
		STUB.unranked.put(STUB.profiles.get(0).id(), 2);
		STUB.dedupe = true;
		alice.send("POST", "/api/job-search/runs/rank", null);
		HttpResponse<String> fullAgain = alice.send("POST", "/api/job-search/runs", null);
		assertEquals(409, fullAgain.statusCode(), fullAgain.body());
		assertEquals("A ranking run is already in progress for this profile", json(fullAgain).path("message").asString());
	}

	@Test
	void anOldJobListThatIgnoresTheRankModeIs503() throws Exception {
		alice.send("PUT", "/api/job-search/profile", PROFILE);
		STUB.unranked.put(STUB.profiles.get(0).id(), 2);
		STUB.omitMode = true;

		assertEquals(503, alice.send("POST", "/api/job-search/runs/rank", null).statusCode());
	}

	@Test
	void aRunWithoutModeIsFull() throws Exception {
		alice.send("PUT", "/api/job-search/profile", PROFILE);
		STUB.omitMode = true;

		assertEquals("FULL", json(alice.send("POST", "/api/job-search/runs", null)).path("mode").asString());
	}

	private static JsonNode json(HttpResponse<String> response) throws Exception {
		return JSON.readTree(response.body());
	}
}
