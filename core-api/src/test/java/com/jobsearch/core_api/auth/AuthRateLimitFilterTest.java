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

/** Which address the auth rate limit counts, and that a forged X-Forwarded-For cannot dodge it. */
class AuthRateLimitFilterTest {

	private static final String PROXY = "127.0.0.1";
	private static final String CLIENT = "203.0.113.7";

	private final AppProperties properties = new AppProperties();
	private AuthRateLimitFilter filter;

	@BeforeEach
	void setUp() {
		properties.getAuthRateLimit().setTrustForwardedHeaders(true);
		properties.getAuthRateLimit().setTrustedProxies("127.0.0.1,10.0.0.2");
		properties.getAuthRateLimit().setLoginPerMinute(3);
		filter = new AuthRateLimitFilter(
				new FixedWindowRateLimiter(), properties, new ApiErrorResponses(JsonMapper.builder().build()));
	}

	@Test
	void forgedFirstAddressesShareTheRealClientsLimit() throws Exception {
		for (int i = 1; i <= 3; i++) {
			assertEquals(200, login(PROXY, "198.51.100." + i + ", " + CLIENT));
		}

		assertEquals(429, login(PROXY, "198.51.100.99, " + CLIENT));
	}

	@Test
	void clientIsTheRightmostAddressThatIsNotATrustedProxy() {
		assertEquals(CLIENT, clientIp(PROXY, CLIENT));
		assertEquals(CLIENT, clientIp(PROXY, "1.1.1.1, " + CLIENT));
		// A second trusted proxy in front of the first one.
		assertEquals(CLIENT, clientIp(PROXY, "1.1.1.1, " + CLIENT + ", 10.0.0.2"));
		assertEquals(CLIENT, clientIp(PROXY, " , " + CLIENT + ",,"));
	}

	@Test
	void aClientOnATrustedAddressIsTrustedLikeAProxy() {
		// A browser on the Vite machine: the proxy appends 127.0.0.1, so the client's own entry is read.
		assertEquals("1.1.1.1", clientIp(PROXY, "1.1.1.1, 127.0.0.1"));
		assertEquals("127.0.0.1", clientIp(PROXY, "127.0.0.1"));
	}

	@Test
	void forwardedHeaderIsIgnoredWhenNotFromATrustedProxy() {
		assertEquals("192.0.2.1", clientIp("192.0.2.1", "1.1.1.1"));

		properties.getAuthRateLimit().setTrustForwardedHeaders(false);
		assertEquals(PROXY, clientIp(PROXY, CLIENT));
	}

	@Test
	void peerIsUsedWhenTheProxySentNoForwardedHeader() {
		assertEquals(PROXY, clientIp(PROXY, null));
	}

	private int login(String peer, String forwardedFor) throws Exception {
		MockHttpServletRequest request = request(peer, forwardedFor);
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());
		return response.getStatus();
	}

	private String clientIp(String peer, String forwardedFor) {
		return AuthRateLimitFilter.clientIp(request(peer, forwardedFor), properties.getAuthRateLimit());
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
