package com.jobsearch.core_api.auth.google;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.common.FixedWindowRateLimiter;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** The browser's side of Google sign-in: the public switch, the invite handed over first, and the trip out to Google. */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"app.google.client-id=test-client-id",
				"app.google.client-secret=test-client-secret-0123456789",
				"app.public-base-url=https://app.example.test"
		})
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class GoogleHttpTest {

	@Value("${local.server.port}")
	private int port;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private FixedWindowRateLimiter rateLimiter;

	// Redirects are not followed: the tests read where the server sends the browser.
	private final HttpClient client = HttpClient.newHttpClient();
	private final Map<String, String> cookies = new LinkedHashMap<>();

	@BeforeEach
	void fetchCsrfToken() throws Exception {
		rateLimiter.clear();
		send("GET", "/api/auth/csrf", null);
	}

	@Test
	void thePublicConfigSaysGoogleIsOn() throws Exception {
		HttpResponse<String> config = send("GET", "/api/auth/config", null);

		assertEquals(200, config.statusCode());
		assertTrue(config.body().contains("\"google\":true"), config.body());
	}

	@Test
	void theTripStartsAtGoogleWithAStateAndTheAppsOwnReturnAddress() throws Exception {
		HttpResponse<String> response = send("GET", "/oauth2/authorization/google", null);

		assertEquals(302, response.statusCode());
		String location = URLDecoder.decode(response.headers().firstValue("location").orElseThrow(), StandardCharsets.UTF_8);
		assertTrue(location.startsWith("https://accounts.google.com/"), location);
		assertTrue(location.contains("state="), location);
		assertTrue(location.contains("scope=openid email profile"), location);
		assertTrue(location.contains("redirect_uri=https://app.example.test/login/oauth2/code/google"), location);
	}

	@Test
	void aReturnWithoutATripOfOurOwnIsRefused() throws Exception {
		HttpResponse<String> response = send("GET", "/login/oauth2/code/google?code=stolen&state=forged", null);

		assertEquals(302, response.statusCode());
		assertEquals("https://app.example.test/login?google=failed", response.headers().firstValue("location").orElseThrow());
	}

	@Test
	void theInviteIsCheckedBeforeTheTripAndAWrongOneStopsItThere() throws Exception {
		String good = "INVITE-" + UUID.randomUUID().toString().toUpperCase();
		jdbc.update("insert into invite_code (code, created_at) values (?, now())", good);

		assertEquals(401, send("POST", "/api/auth/google/start", "{\"inviteCode\":\"NO-SUCH-INVITE\"}").statusCode());
		assertEquals(204, send("POST", "/api/auth/google/start", "{\"inviteCode\":\"" + good + "\"}").statusCode());
		assertEquals(0, jdbc.queryForObject("select count(*) from invite_code where code = ? and used_at is not null", Integer.class, good),
				"asking to start does not spend the invite");
		assertEquals(400, send("POST", "/api/auth/google/start", "{}").statusCode());
	}

	@Test
	void startNeedsTheCsrfToken() throws Exception {
		cookies.remove("XSRF-TOKEN");

		HttpResponse<String> response = send("POST", "/api/auth/google/start", "{\"inviteCode\":\"X\"}");

		assertEquals(403, response.statusCode());
		assertFalse(response.body().contains("email_not_verified"));
	}

	private HttpResponse<String> send(String method, String path, String json) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
		if (!cookies.isEmpty()) {
			builder.header("Cookie", String.join("; ", cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList()));
		}
		if (cookies.containsKey("XSRF-TOKEN")) {
			builder.header("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
		}
		if (json != null) {
			builder.header("Content-Type", "application/json");
		}
		builder.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
		HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
		for (String setCookie : response.headers().allValues("set-cookie")) {
			String pair = setCookie.substring(0, setCookie.indexOf(';'));
			cookies.put(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1));
		}
		return response;
	}
}
