package com.jobsearch.core_api.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
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
}
