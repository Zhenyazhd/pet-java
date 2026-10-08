package com.jobsearch.core_api.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

class ApiExceptionHandlerTest {

	private final ApiExceptionHandler handler = new ApiExceptionHandler(new ApiErrorResponses(JsonMapper.builder().build()));

	@Test
	void serviceUnavailableIs503WithRetryAfterWhenKnown() {
		ResponseEntity<Map<String, Object>> busy =
				handler.handleServiceUnavailable(new ServiceUnavailableException("busy", Duration.ofSeconds(30)));
		ResponseEntity<Map<String, Object>> down =
				handler.handleServiceUnavailable(new ServiceUnavailableException("down", null));

		assertEquals(503, busy.getStatusCode().value());
		assertEquals("30", busy.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
		assertEquals("busy", busy.getBody().get("message"));
		assertNull(down.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
	}

	@RestController
	static class Probe {
		@PostMapping("/probe")
		String post(@RequestBody Map<String, String> body) {
			return "ok";
		}

		@GetMapping("/probe/{id}")
		String get(@PathVariable UUID id) {
			return "ok";
		}
	}

	private MockMvc mvc() {
		return MockMvcBuilders.standaloneSetup(new Probe()).setControllerAdvice(handler).build();
	}

	@Test
	void malformedJsonIs400WithSharedBody() throws Exception {
		mvc().perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{oops"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").value("Failed to read request"));
	}

	@Test
	void typeMismatchIs400() throws Exception {
		mvc().perform(get("/probe/not-a-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void wrongMethodIs405WithAllowHeader() throws Exception {
		mvc().perform(put("/probe"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(header().exists(HttpHeaders.ALLOW))
				.andExpect(jsonPath("$.status").value(405));
	}

	@Test
	void wrongContentTypeIs415() throws Exception {
		mvc().perform(post("/probe").contentType(MediaType.TEXT_PLAIN).content("x"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.status").value(415));
	}

	@Test
	void unexpectedFailuresDoNotLeakTheirMessage() {
		ResponseEntity<Map<String, Object>> response =
				handler.handleUnexpected(new IllegalStateException("secret bucket name"));

		assertEquals(500, response.getStatusCode().value());
		assertEquals("Internal server error", response.getBody().get("message"));
	}

	@Test
	void aStrayIllegalArgumentIsAServerErrorNotTheUsersMistake() {
		ResponseEntity<Map<String, Object>> response =
				handler.handleUnexpected(new IllegalArgumentException("password cannot be more than 72 bytes"));

		assertEquals(500, response.getStatusCode().value());
		assertEquals("Internal server error", response.getBody().get("message"));
	}

	@Test
	void aBadRequestExceptionCarriesItsOwnMessageToTheUser() {
		ResponseEntity<Map<String, Object>> response =
				handler.handleBadRequest(new BadRequestException("Resume is empty — fill the sheet before matching"));

		assertEquals(400, response.getStatusCode().value());
		assertEquals("Resume is empty — fill the sheet before matching", response.getBody().get("message"));
	}
}
