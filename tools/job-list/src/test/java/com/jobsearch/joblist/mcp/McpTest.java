package com.jobsearch.joblist.mcp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobsearch.joblist.TestData;
import com.jobsearch.joblist.TestcontainersConfiguration;
import com.jobsearch.joblist.job.Job;
import com.jobsearch.joblist.job.JobRepository;
import com.jobsearch.joblist.rank.Match;
import com.jobsearch.joblist.rank.MatchRepository;
import com.jobsearch.joblist.source.SourceName;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class McpTest {

	private static final String BOT = "Bearer test-token-telegram-bot";

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private JobRepository jobs;
	@Autowired
	private MatchRepository matches;

	@BeforeEach
	void setUp() {
		TestData.reset(jdbc);
	}

	@Test
	void theEndpointNeedsTheBearerToken() throws Exception {
		mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
	}

	@Test
	void initializeAnswersWithTheRequestedVersionAndTheToolsCapability() throws Exception {
		rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\"}}")
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.result.protocolVersion").value("2025-03-26"))
				.andExpect(jsonPath("$.result.capabilities.tools").exists());
	}

	@Test
	void notificationsGetNoBodyAndTheStreamIsNotOffered() throws Exception {
		mvc.perform(post("/mcp").header("Authorization", BOT).contentType(MediaType.APPLICATION_JSON)
				.content("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")).andExpect(status().isAccepted());
		mvc.perform(get("/mcp").header("Authorization", BOT)).andExpect(status().isMethodNotAllowed());
	}

	@Test
	void toolsAreListedWithTheirSchemas() throws Exception {
		rpc("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}")
				.andExpect(jsonPath("$.result.tools.length()").value(6))
				.andExpect(jsonPath("$.result.tools[?(@.name=='get_matches')].inputSchema.required[0]").value("profileId"));
	}

	@Test
	void savedProfileCanBeListedAndAnInvalidOneIsAToolErrorTheAgentCanRead() throws Exception {
		String save = "{\"ownerRef\":\"chat-1\",\"name\":\"Main\",\"expectations\":\"Python jobs.\","
				+ "\"roles\":[\"python developer\"],\"regions\":[{\"country\":\"FR\"}]}";
		call("save_profile", save).andExpect(jsonPath("$.result.isError").value(false));
		call("save_profile", save).andExpect(jsonPath("$.result.isError").value(false));
		call("list_profiles", "{\"ownerRef\":\"chat-1\"}")
				.andExpect(jsonPath("$.result.content[0].text").value(org.hamcrest.Matchers.containsString("\"name\":\"Main\"")));

		call("save_profile", "{\"ownerRef\":\"chat-1\",\"name\":\"Bad\",\"expectations\":\"x\",\"roles\":[\"python developer\"],\"regions\":[{\"country\":\"XX\"}]}")
				.andExpect(jsonPath("$.result.isError").value(true))
				.andExpect(jsonPath("$.result.content[0].text").value(org.hamcrest.Matchers.containsString("ISO")));
	}

	@Test
	void usageIsAvailableAndAnUnknownToolOrMethodIsAProtocolError() throws Exception {
		call("get_usage", "{}").andExpect(jsonPath("$.result.content[0].text").value(org.hamcrest.Matchers.containsString("budgetUsd")));
		call("delete_profile", "{}").andExpect(jsonPath("$.error.code").value(-32602));
		rpc("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"resources/list\"}").andExpect(jsonPath("$.error.code").value(-32601));
	}

	@Test
	void matchesCarryAnUntrustedTextNoteAndOtherClientsProfilesAreNotFound() throws Exception {
		String save = "{\"ownerRef\":\"chat-1\",\"name\":\"Main\",\"expectations\":\"Python jobs.\","
				+ "\"roles\":[\"python developer\"]}";
		call("save_profile", save).andExpect(jsonPath("$.result.isError").value(false));

		call("get_matches", "{\"profileId\":1}")
				.andExpect(jsonPath("$.result.content[0].text").value(org.hamcrest.Matchers.containsString("third-party")))
				.andExpect(jsonPath("$.result.content[0].text").value(org.hamcrest.Matchers.containsString("\"matches\":[]")));
		mvc.perform(post("/mcp").header("Authorization", "Bearer test-token-core-api").contentType(MediaType.APPLICATION_JSON)
						.content("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\",\"params\":{\"name\":\"get_matches\",\"arguments\":{\"profileId\":1}}}"))
				.andExpect(jsonPath("$.result.isError").value(true))
				.andExpect(jsonPath("$.result.content[0].text").value("Profile not found: 1"));
		call("mark_delivered", "{\"matchId\":999}").andExpect(jsonPath("$.result.isError").value(true));
	}

	@Test
	void getMatchesHidesMatchesOlderThanTheAgeLimitAndUsageShowsTheLimit() throws Exception {
		call("save_profile", "{\"ownerRef\":\"chat-1\",\"name\":\"Main\",\"expectations\":\"Python jobs.\",\"roles\":[\"python developer\"]}")
				.andExpect(jsonPath("$.result.isError").value(false));
		long profileId = jdbc.queryForObject("select id from search_profile", Long.class);
		Instant now = Instant.now();
		match(profileId, "Fresh match", now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(1)));
		match(profileId, "Stale match", now.minus(Duration.ofDays(5)), now.minus(Duration.ofHours(1)));

		call("get_matches", "{\"profileId\":" + profileId + ",\"minScore\":0}")
				.andExpect(jsonPath("$.result.content[0].text").value(org.hamcrest.Matchers.containsString("Fresh match")))
				.andExpect(jsonPath("$.result.content[0].text").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Stale match"))));
		call("get_usage", "{}").andExpect(jsonPath("$.result.content[0].text").value(org.hamcrest.Matchers.containsString("\"maxPostingAgeDays\":3")));
	}

	private void match(long profileId, String title, Instant postedAt, Instant fetchedAt) {
		Job job = TestData.job(SourceName.HELLOWORK, title);
		job.setFingerprint(job.getExternalId());
		job.setPostedAt(postedAt);
		job.setFetchedAt(fetchedAt);
		Match match = Match.of(profileId, jobs.save(job));
		match.rank(90, "Reason", "hash");
		matches.save(match);
	}

	@Test
	void argumentsOfTheWrongTypeAreAToolErrorAndAnAnswerFromTheClientIsAccepted() throws Exception {
		call("save_profile", "{\"ownerRef\":\"c\",\"name\":\"n\",\"expectations\":\"e\",\"roles\":[\"x\"],\"minScore\":\"high\"}")
				.andExpect(jsonPath("$.result.isError").value(true))
				.andExpect(jsonPath("$.result.content[0].text").value(org.hamcrest.Matchers.containsString("could not be read")));
		mvc.perform(post("/mcp").header("Authorization", BOT).contentType(MediaType.APPLICATION_JSON)
				.header("Accept", "application/json, text/event-stream").content("{\"jsonrpc\":\"2.0\",\"id\":5,\"result\":{}}"))
				.andExpect(status().isAccepted());
	}

	private ResultActions call(String tool, String arguments) throws Exception {
		return rpc("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\",\"params\":{\"name\":\"" + tool + "\",\"arguments\":" + arguments + "}}");
	}

	private ResultActions rpc(String body) throws Exception {
		return mvc.perform(post("/mcp").header("Authorization", BOT).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
	}
}
