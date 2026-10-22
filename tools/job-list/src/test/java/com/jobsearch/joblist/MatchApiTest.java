package com.jobsearch.joblist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobsearch.joblist.job.Job;
import com.jobsearch.joblist.job.JobRepository;
import com.jobsearch.joblist.profile.ProfileDtos.ProfileRequest;
import com.jobsearch.joblist.profile.SearchProfileService;
import com.jobsearch.joblist.rank.Match;
import com.jobsearch.joblist.rank.MatchRepository;
import com.jobsearch.joblist.source.Region;
import com.jobsearch.joblist.source.SourceName;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MatchApiTest {

	private static final String CORE_API = "Bearer test-token-core-api";
	private static final String BOT = "Bearer test-token-telegram-bot";

	@Autowired
	private MockMvc mvc;
	@Autowired
	private SearchProfileService profiles;
	@Autowired
	private JobRepository jobs;
	@Autowired
	private MatchRepository matches;
	@Autowired
	private JdbcTemplate jdbc;

	private long profileId;
	private long bestMatchId;

	@BeforeEach
	void setUp() {
		TestData.reset(jdbc);
		profileId = profiles.create("core-api", new ProfileRequest("owner", "P", "Anything.", List.of("python developer"),
				List.of(new Region("FR", null)), List.of(SourceName.HELLOWORK), 70)).id();
		bestMatchId = match("Best job", 95).getId();
		match("Good job", 80);
		match("Weak job", 30);
	}

	@Test
	void matchesComeBestFirstAboveTheProfilesMinimumScore() throws Exception {
		mvc.perform(get("/v1/profiles/" + profileId + "/matches").header("Authorization", CORE_API))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].job.title").value("Best job"))
				.andExpect(jsonPath("$.items[0].score").value(95))
				.andExpect(jsonPath("$.items[0].job.snippet").isNotEmpty())
				.andExpect(jsonPath("$.nextCursor").doesNotExist());
	}

	@Test
	void pagesFollowTheCursorAndMinScoreCanBeLowered() throws Exception {
		String cursor = mvc.perform(get("/v1/profiles/" + profileId + "/matches?limit=1&minScore=0").header("Authorization", CORE_API))
				.andExpect(jsonPath("$.items[0].job.title").value("Best job"))
				.andReturn().getResponse().getContentAsString().replaceAll(".*\"nextCursor\":\"([^\"]+)\".*", "$1");

		mvc.perform(get("/v1/profiles/" + profileId + "/matches?limit=2&minScore=0&cursor=" + cursor).header("Authorization", CORE_API))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].job.title").value("Good job"))
				.andExpect(jsonPath("$.items[1].job.title").value("Weak job"));
	}

	@Test
	void deliveredMatchesDisappearFromTheDefaultListButCanBeAskedFor() throws Exception {
		mvc.perform(post("/v1/matches/" + bestMatchId + "/ack").header("Authorization", CORE_API)).andExpect(status().isNoContent());

		mvc.perform(get("/v1/profiles/" + profileId + "/matches").header("Authorization", CORE_API))
				.andExpect(jsonPath("$.items.length()").value(1));
		mvc.perform(get("/v1/profiles/" + profileId + "/matches?delivered=true").header("Authorization", CORE_API))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].job.title").value("Best job"))
				.andExpect(jsonPath("$.items[0].deliveredAt").isNotEmpty());
	}

	@Test
	void anotherClientSeesNothingAndBadParametersAreRefused() throws Exception {
		mvc.perform(get("/v1/profiles/" + profileId + "/matches").header("Authorization", BOT)).andExpect(status().isNotFound());
		mvc.perform(post("/v1/matches/" + bestMatchId + "/ack").header("Authorization", BOT)).andExpect(status().isNotFound());
		mvc.perform(get("/v1/profiles/" + profileId + "/matches?limit=0").header("Authorization", CORE_API)).andExpect(status().isBadRequest());
		mvc.perform(get("/v1/profiles/" + profileId + "/matches?cursor=nonsense").header("Authorization", CORE_API)).andExpect(status().isBadRequest());
		mvc.perform(get("/v1/profiles/" + profileId + "/matches?since=yesterday").header("Authorization", CORE_API)).andExpect(status().isBadRequest());
	}

	@Test
	void matchesOlderThanTheAgeLimitAreHiddenWhateverTheirScoreAndCursorPagingSurvivesTheBoundary() throws Exception {
		Instant now = Instant.now();
		match("Old but top", 99, now.minus(Duration.ofDays(4)), now.minus(Duration.ofHours(1)));
		match("Undated, fetched long ago", 98, null, now.minus(Duration.ofDays(5)));
		match("Undated, fetched today", 85, null, now.minus(Duration.ofHours(2)));
		match("Posted 2 days ago", 75, now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(2)));

		mvc.perform(get("/v1/profiles/" + profileId + "/matches?minScore=0&limit=100").header("Authorization", CORE_API))
				.andExpect(jsonPath("$.items.length()").value(5))
				.andExpect(jsonPath("$.items[0].job.title").value("Best job"))
				.andExpect(jsonPath("$.items[1].job.title").value("Undated, fetched today"))
				.andExpect(jsonPath("$.items[3].job.title").value("Posted 2 days ago"))
				.andExpect(jsonPath("$.items[4].job.title").value("Weak job"));

		// pages of two walk through the same visible set, with no gap or repeat where the hidden matches would be
		List<String> seen = new java.util.ArrayList<>();
		String cursor = null;
		do {
			String body = mvc.perform(get("/v1/profiles/" + profileId + "/matches?minScore=0&limit=2" + (cursor == null ? "" : "&cursor=" + cursor))
					.header("Authorization", CORE_API)).andReturn().getResponse().getContentAsString();
			var json = new tools.jackson.databind.ObjectMapper().readTree(body);
			json.get("items").forEach(item -> seen.add(item.get("job").get("title").asString()));
			cursor = json.hasNonNull("nextCursor") ? json.get("nextCursor").asString() : null;
		}
		while (cursor != null);
		org.junit.jupiter.api.Assertions.assertEquals(
				List.of("Best job", "Undated, fetched today", "Good job", "Posted 2 days ago", "Weak job"), seen);
	}

	@Test
	void aMatchDisappearsWhenItsJobTurnsOlderThanTheLimit() throws Exception {
		Instant now = Instant.now();
		Match aging = match("Aging", 99, now.minus(Duration.ofDays(2)).minus(Duration.ofHours(23)), now.minus(Duration.ofDays(2)));
		mvc.perform(get("/v1/profiles/" + profileId + "/matches").header("Authorization", CORE_API))
				.andExpect(jsonPath("$.items[0].job.title").value("Aging"));

		jdbc.update("update job set posted_at = posted_at - interval '2 hours' where id = ?", aging.getJob().getId());

		mvc.perform(get("/v1/profiles/" + profileId + "/matches").header("Authorization", CORE_API))
				.andExpect(jsonPath("$.items[0].job.title").value("Best job"));
		mvc.perform(get("/v1/profiles/" + profileId + "/matches?delivered=true").header("Authorization", CORE_API))
				.andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	void usageTellsClientsTheAgeLimit() throws Exception {
		mvc.perform(get("/v1/usage").header("Authorization", CORE_API)).andExpect(jsonPath("$.maxPostingAgeDays").value(3));
	}

	private Match match(String title, int score) {
		return match(title, score, null, Instant.now());
	}

	private Match match(String title, int score, Instant postedAt, Instant fetchedAt) {
		Job job = TestData.job(SourceName.HELLOWORK, title);
		job.setFingerprint(job.getExternalId());
		job.setPostedAt(postedAt);
		job.setFetchedAt(fetchedAt);
		Match match = Match.of(profileId, jobs.save(job));
		match.rank(score, "Reason for " + title, "hash");
		return matches.save(match);
	}
}
