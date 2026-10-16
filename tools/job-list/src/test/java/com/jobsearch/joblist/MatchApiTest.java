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

	private Match match(String title, int score) {
		Job job = TestData.job(SourceName.HELLOWORK, title);
		job.setFingerprint(job.getExternalId());
		job.setFetchedAt(java.time.Instant.now());
		Match match = Match.of(profileId, jobs.save(job));
		match.rank(score, "Reason for " + title, "hash");
		return matches.save(match);
	}
}
