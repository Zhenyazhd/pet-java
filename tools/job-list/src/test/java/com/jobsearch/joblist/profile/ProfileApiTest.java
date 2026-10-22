package com.jobsearch.joblist.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobsearch.joblist.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProfileApiTest {

	private static final String CORE_API = "Bearer test-token-core-api";
	private static final String BOT = "Bearer test-token-telegram-bot";

	@Autowired
	private MockMvc mvc;

	@Test
	void requestsWithoutAValidTokenAreRefused() throws Exception {
		mvc.perform(get("/v1/profiles")).andExpect(status().isUnauthorized());
		mvc.perform(get("/v1/profiles").header("Authorization", "Bearer wrong-token-value")).andExpect(status().isUnauthorized());
		mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
	}

	@Test
	void profileCanBeCreatedReadUpdatedAndDeleted() throws Exception {
		String owner = "user-" + UUID.randomUUID();
		String id = createProfile(CORE_API, owner, "Backend").andExpect(status().isCreated())
				.andExpect(jsonPath("$.minScore").value(70))
				.andExpect(jsonPath("$.sources.length()").value(3))
				.andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");

		mvc.perform(get("/v1/profiles?ownerRef=" + owner).header("Authorization", CORE_API))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
		mvc.perform(put("/v1/profiles/" + id).header("Authorization", CORE_API).contentType(MediaType.APPLICATION_JSON)
						.content(body(owner, "Backend", "[\"java developer\"]", "[]", "[\"WTTJ\"]")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.sources[0]").value("WTTJ"));
		mvc.perform(delete("/v1/profiles/" + id).header("Authorization", CORE_API)).andExpect(status().isNoContent());
		mvc.perform(get("/v1/profiles/" + id).header("Authorization", CORE_API)).andExpect(status().isNotFound());
	}

	@Test
	void anotherClientsProfileLooksMissing() throws Exception {
		String id = createProfile(CORE_API, "user-" + UUID.randomUUID(), "Mine").andReturn().getResponse().getContentAsString()
				.replaceAll(".*\"id\":(\\d+).*", "$1");

		mvc.perform(get("/v1/profiles/" + id).header("Authorization", BOT)).andExpect(status().isNotFound());
		mvc.perform(delete("/v1/profiles/" + id).header("Authorization", BOT)).andExpect(status().isNotFound());
	}

	@Test
	void invalidRequestsAreRejected() throws Exception {
		String owner = "user-" + UUID.randomUUID();
		mvc.perform(post("/v1/profiles").header("Authorization", CORE_API).contentType(MediaType.APPLICATION_JSON)
						.content(body(owner, "Bad", "[]", "[]", "null")))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/v1/profiles").header("Authorization", CORE_API).contentType(MediaType.APPLICATION_JSON)
						.content(body(owner, "Bad", "[\"java developer\"]", "[{\"country\":\"XX\"}]", "null")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ISO")));
		mvc.perform(post("/v1/profiles").header("Authorization", CORE_API).contentType(MediaType.APPLICATION_JSON)
						.content(body(owner, "Bad", "[\"java developer\"]", "[]", "[\"LINKEDIN\"]")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not enabled")));
	}

	@Test
	void ownerCannotExceedTheProfileLimitOrReuseAName() throws Exception {
		String owner = "user-" + UUID.randomUUID();
		createProfile(CORE_API, owner, "One").andExpect(status().isCreated());
		createProfile(CORE_API, owner, "One").andExpect(status().isBadRequest());
		createProfile(CORE_API, owner, "Two").andExpect(status().isCreated());
		createProfile(CORE_API, owner, "Three").andExpect(status().isBadRequest());
	}

	private ResultActions createProfile(String token, String owner, String name) throws Exception {
		return mvc.perform(post("/v1/profiles").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content(body(owner, name, "[\"python developer\"]", "[{\"country\":\"FR\"}]", "null")));
	}

	private static String body(String owner, String name, String roles, String regions, String sources) {
		return """
				{"ownerRef": "%s", "name": "%s", "expectations": "Backend roles, remote friendly.",
				 "roles": %s, "regions": %s, "sources": %s}
				""".formatted(owner, name, roles, regions, sources);
	}
}
