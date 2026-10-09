package com.jobsearch.core_api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.common.FixedWindowRateLimiter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Registering and signing in over real HTTP. Registration hashes the password before its transaction opens, so the
 * invite lock, the new user and the used mark must still stand or fall together.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		// Shows the components behind each probe, to check what readiness and liveness are made of.
		properties = "management.endpoint.health.show-details=always")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RegistrationHttpTest {

	private static final String PASSWORD = "correct horse battery";

	@Value("${local.server.port}")
	private int port;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private FixedWindowRateLimiter rateLimiter;

	private final HttpClient client = HttpClient.newHttpClient();
	private final Map<String, String> cookies = new LinkedHashMap<>();

	@BeforeEach
	void fetchCsrfToken() throws Exception {
		rateLimiter.clear();
		send("GET", "/api/auth/csrf", null);
	}

	@Test
	void anInviteTurnsIntoAnAccountThatCanSignIn() throws Exception {
		String invite = newInvite();
		String email = newEmail();

		HttpResponse<String> registered = register(email, PASSWORD, invite);
		HttpResponse<String> me = send("GET", "/api/auth/me", null);

		assertEquals(201, registered.statusCode(), registered.body());
		assertEquals(200, me.statusCode(), me.body());
		assertTrue(me.body().contains(email), me.body());
		assertEquals(1, jdbc.queryForObject("select count(*) from invite_code where code = ? and used_at is not null", Integer.class, invite));
		cookies.remove("JSESSIONID");
		assertEquals(200, send("POST", "/api/auth/login", credentials(email, PASSWORD)).statusCode());
	}

	@Test
	void aWrongInviteIsRefusedAndCreatesNothing() throws Exception {
		String email = newEmail();

		HttpResponse<String> response = register(email, PASSWORD, "no-such-invite");

		assertEquals(401, response.statusCode(), response.body());
		assertEquals(0, jdbc.queryForObject("select count(*) from app_user where email = ?", Integer.class, email));
	}

	@Test
	void anInviteWorksOnce() throws Exception {
		String invite = newInvite();
		assertEquals(201, register(newEmail(), PASSWORD, invite).statusCode());

		HttpResponse<String> second = register(newEmail(), PASSWORD, invite);

		assertEquals(401, second.statusCode(), second.body());
	}

	@Test
	void aTakenEmailFailsWithoutSpendingTheInvite() throws Exception {
		String email = newEmail();
		assertEquals(201, register(email, PASSWORD, newInvite()).statusCode());
		String unused = newInvite();

		HttpResponse<String> response = register(email.toUpperCase(), PASSWORD, unused);

		assertEquals(409, response.statusCode(), response.body());
		assertEquals(0, jdbc.queryForObject("select count(*) from invite_code where code = ? and used_at is not null", Integer.class, unused),
				"the failed registration rolled back with the invite still free");
		assertEquals(201, register(newEmail(), PASSWORD, unused).statusCode(), "and the invite can still be used");
	}

	@Test
	void aPasswordLongerThanBcryptCountsIsRefusedWithAClearMessage() throws Exception {
		HttpResponse<String> response = register(newEmail(), "я".repeat(40), newInvite());

		assertEquals(400, response.statusCode(), response.body());
		assertTrue(response.body().contains("72 bytes"), response.body());
		assertFalse(response.body().contains("cannot be more than"), "the library's own message is not shown");
	}

	@Test
	void readinessDependsOnTheDatabaseAndLivenessDoesNot() throws Exception {
		HttpResponse<String> readiness = send("GET", "/actuator/health/readiness", null);
		HttpResponse<String> liveness = send("GET", "/actuator/health/liveness", null);

		assertEquals(200, readiness.statusCode());
		assertEquals(200, liveness.statusCode());
		assertTrue(readiness.body().contains("\"db\""), readiness.body());
		assertFalse(liveness.body().contains("\"db\""), liveness.body());
		assertEquals(404, send("GET", "/actuator/env", null).statusCode(), "nothing but health is exposed");
	}

	@Test
	void twoRegistrationsOnOneInviteAtOnceLeaveExactlyOneAccount() throws Exception {
		String invite = newInvite();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch go = new CountDownLatch(1);
			List<Future<Integer>> results = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				String email = newEmail();
				results.add(pool.submit(() -> {
					go.await();
					return registerWithFreshSession(email, invite);
				}));
			}
			go.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> result : results) {
				statuses.add(result.get(30, TimeUnit.SECONDS));
			}

			assertEquals(List.of(201, 401), statuses.stream().sorted().toList());
			assertEquals(1, jdbc.queryForObject("select count(*) from invite_code where code = ? and used_at is not null", Integer.class, invite));
			assertEquals(1, jdbc.queryForObject("select count(*) from app_user where id in (select used_by_user_id from invite_code where code = ?)", Integer.class, invite));
		}
		finally {
			pool.shutdownNow();
		}
	}

	/** Its own cookies, as a second browser would have. */
	private int registerWithFreshSession(String email, String invite) throws Exception {
		RegistrationHttpTest browser = new RegistrationHttpTest();
		browser.port = port;
		browser.jdbc = jdbc;
		browser.rateLimiter = rateLimiter;
		browser.fetchCsrfToken();
		return browser.register(email, PASSWORD, invite).statusCode();
	}

	private String newInvite() {
		String code = "invite-" + UUID.randomUUID();
		jdbc.update("insert into invite_code (code, created_at) values (?, now())", code);
		return code;
	}

	private static String newEmail() {
		return "user-" + UUID.randomUUID() + "@test.local";
	}

	private HttpResponse<String> register(String email, String password, String invite) throws Exception {
		return send("POST", "/api/auth/register",
				"{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"displayName\":\"T\",\"inviteCode\":\"" + invite + "\"}");
	}

	private static String credentials(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
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
