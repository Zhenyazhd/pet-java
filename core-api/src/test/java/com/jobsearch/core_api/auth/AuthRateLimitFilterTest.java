package com.jobsearch.core_api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jobsearch.core_api.common.ApiErrorResponses;
import com.jobsearch.core_api.common.FixedWindowRateLimiter;
import com.jobsearch.core_api.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** The auth rate limit counts the container-resolved address; a forged X-Forwarded-For cannot dodge it. */
class AuthRateLimitFilterTest {

	private static final String CLIENT = "203.0.113.7";

	private final AppProperties properties = new AppProperties();
	private AuthRateLimitFilter filter;

	@BeforeEach
	void setUp() {
		properties.getAuthRateLimit().setLoginPerMinute(3);
		filter = new AuthRateLimitFilter(
				new FixedWindowRateLimiter(), properties, new ApiErrorResponses(JsonMapper.builder().build()));
	}

	@Test
	void forgedForwardedForDoesNotChangeTheBucket() throws Exception {
		for (int i = 1; i <= 3; i++) {
			assertEquals(200, login(CLIENT, "198.51.100." + i));
		}

		assertEquals(429, login(CLIENT, "198.51.100.99"));
	}

	@Test
	void differentPeersHaveSeparateLimits() throws Exception {
		for (int i = 0; i < 3; i++) {
			login(CLIENT, null);
		}

		assertEquals(429, login(CLIENT, null));
		assertEquals(200, login("203.0.113.8", null));
	}

	@Test
	void otherPathsAndMethodsAreNotLimited() throws Exception {
		for (int i = 0; i < 5; i++) {
			MockHttpServletRequest request = request(CLIENT, null);
			request.setRequestURI("/api/auth/me");
			MockHttpServletResponse response = new MockHttpServletResponse();
			filter.doFilter(request, response, new MockFilterChain());
			assertEquals(200, response.getStatus());
		}
	}

	private int login(String peer, String forwardedFor) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request(peer, forwardedFor), response, new MockFilterChain());
		return response.getStatus();
	}

	private static MockHttpServletRequest request(String peer, String forwardedFor) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
		request.setRemoteAddr(peer);
		if (forwardedFor != null) {
			request.addHeader("X-Forwarded-For", forwardedFor);
		}
		return request;
	}
}
