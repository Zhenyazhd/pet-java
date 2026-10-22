package com.jobsearch.core_api.jobsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.auth.AppUserRepository;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Without JOB_LIST_TOKEN the app starts and the feature is reported off. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.job-list.token=")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class JobSearchDisabledTest {

	@Value("${local.server.port}")
	private int port;
	@Autowired
	private AppUserRepository users;
	@Autowired
	private PasswordEncoder encoder;

	@Test
	void stateSaysNotConfiguredAndTheRestIs503() throws Exception {
		Browser user = new Browser(port, users, encoder, true);

		HttpResponse<String> state = user.send("GET", "/api/job-search", null);
		assertEquals(200, state.statusCode());
		JsonNode body = JsonMapper.builder().build().readTree(state.body());
		assertFalse(body.path("configured").asBoolean());
		assertTrue(body.path("profile").isNull());
		assertTrue(body.path("latestRun").isNull());
		assertEquals(0, body.path("unranked").asInt(-1));

		String profile = "{\"expectations\":\"x\",\"roles\":[\"r\"]}";
		for (HttpResponse<String> response : new HttpResponse[] {
				user.send("PUT", "/api/job-search/profile", profile),
				user.send("POST", "/api/job-search/runs", null),
				user.send("POST", "/api/job-search/runs/rank", null),
				user.send("GET", "/api/job-search/matches", null),
				user.send("POST", "/api/job-search/matches/1/seen", null) }) {
			assertEquals(503, response.statusCode(), response.body());
			assertTrue(response.body().contains("job search is not configured"), response.body());
		}
	}
}
