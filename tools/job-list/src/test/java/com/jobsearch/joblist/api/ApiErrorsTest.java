package com.jobsearch.joblist.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobsearch.joblist.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Client mistakes are 4xx in the one error shape, not 500. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ApiErrorsTest {

	private static final String TOKEN = "Bearer test-token-core-api";

	@Autowired
	private MockMvc mvc;

	@Test
	void malformedBodyWrongTypeWrongMethodAndUnknownPathAreClientErrors() throws Exception {
		mvc.perform(post("/v1/profiles").header("Authorization", TOKEN).contentType(MediaType.APPLICATION_JSON).content("{not json"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.message").isNotEmpty());
		mvc.perform(get("/v1/profiles/abc").header("Authorization", TOKEN)).andExpect(status().isBadRequest());
		mvc.perform(get("/v1/profiles/1/matches?limit=x").header("Authorization", TOKEN)).andExpect(status().isBadRequest());
		mvc.perform(delete("/mcp").header("Authorization", TOKEN)).andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.status").value(405));
		mvc.perform(get("/v1/nothing-here").header("Authorization", TOKEN)).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
	}
}
