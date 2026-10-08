package com.jobsearch.core_api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.common.FixedWindowRateLimiter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * The rate limits hold over a real HTTP connection. A percent-encoded path ({@code /api/auth/log%69n}) reaches
 * the same controller as the plain one, so it must count against the same bucket; matching on the raw request
 * URI let it through unlimited. A mock request with a literal path cannot show this, only a real server can.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"app.auth-rate-limit.login-per-minute=2",
				"app.auth-rate-limit.register-per-minute=2",
				"app.expensive-ops-rate-limit.per-user-per-minute=2"
		})
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RateLimitPathBypassTest {

	private static final String LOGIN_BODY = "{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}";

	@Value("${local.server.port}")
	private int port;
	@Autowired
	private FixedWindowRateLimiter rateLimiter;

	private final HttpClient client = HttpClient.newHttpClient();
	private String csrfToken;

	@BeforeEach
	void fetchCsrfTokenAndForgetEarlierAttempts() throws Exception {
		rateLimiter.clear();
		HttpResponse<String> response = client.send(
				HttpRequest.newBuilder(uri("/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
		List<String> cookies = response.headers().allValues("set-cookie");
		csrfToken = cookies.stream()
				.filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
				.map(cookie -> cookie.substring("XSRF-TOKEN=".length(), cookie.indexOf(';')))
				.findFirst()
				.orElseThrow();
	}

	/** Every limited path, once as the plain route and once with a percent-escape in it. */
	static Stream<Arguments> limitedPaths() {
		return Stream.of(
				Arguments.of("/api/auth/login", "/api/auth/log%69n", LOGIN_BODY),
				Arguments.of("/api/auth/register", "/api/auth/regist%65r", "{}"),
				Arguments.of("/api/resume/compile", "/api/resume/compil%65", "{}"),
				Arguments.of("/api/vacancies/import", "/api/vacancies/impor%74", "{}"),
				Arguments.of("/api/ai/resume/suggest", "/api/ai/resume/sugges%74", "{}"),
				Arguments.of("/api/ai/resume/match", "/api/%61i/resume/match", "{}"));
	}

	@ParameterizedTest(name = "{1} counts against {0}")
	@MethodSource("limitedPaths")
	void encodedPathSharesTheBucketOfThePlainPath(String plain, String encoded, String body) throws Exception {
		assertNotEquals(429, post(encoded, body).statusCode());
		assertNotEquals(429, post(encoded, body).statusCode());

		assertEquals(429, post(plain, body).statusCode(), "the plain path sees the attempts made through the encoded one");
		assertEquals(429, post(encoded, body).statusCode());
	}

	@Test
	void limitedResponseSaysWhenToRetry() throws Exception {
		post("/api/auth/login", LOGIN_BODY);
		post("/api/auth/login", LOGIN_BODY);

		HttpResponse<String> limited = post("/api/auth/login", LOGIN_BODY);

		assertEquals(429, limited.statusCode());
		assertTrue(limited.headers().firstValue("Retry-After").isPresent());
	}

	@Test
	void encodingAPathDoesNotSkipAuthorization() throws Exception {
		assertEquals(401, get("/api/vacancies"));
		assertEquals(401, get("/%61pi/vacancies"));
		assertEquals(401, get("/api/%76acancies"));
		assertEquals(401, get("/api/%69nvite-codes"));
	}

	/** Variants where the raw text and the routed path could differ are refused before any filter or controller. */
	@ParameterizedTest
	@MethodSource("refusedPaths")
	void ambiguousPathsAreRefused(String path) throws Exception {
		assertEquals(400, post(path, LOGIN_BODY).statusCode());
	}

	static Stream<String> refusedPaths() {
		return Stream.of("/api//auth/login", "/api/auth/login;x=1", "/api/auth%2Flogin", "/api/./auth/login");
	}

	/** A trailing slash or a suffix is not the login route: Spring Security answers 401 before the controller. */
	@ParameterizedTest
	@MethodSource("lookalikePaths")
	void lookalikePathsNeverReachTheController(String path) throws Exception {
		HttpResponse<String> response = post(path, LOGIN_BODY);

		assertEquals(401, response.statusCode());
		assertTrue(response.body().contains("Unauthorized"), response.body());
	}

	static Stream<String> lookalikePaths() {
		return Stream.of("/api/auth/login/", "/api/auth/login.json");
	}

	private HttpResponse<String> post(String path, String json) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(uri(path))
				.header("Content-Type", "application/json")
				.header("Cookie", "XSRF-TOKEN=" + csrfToken)
				.header("X-XSRF-TOKEN", csrfToken)
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private int get(String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(uri(path)).GET().build();
		return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	/** {@link URI#create} keeps the percent-escapes exactly as written, which is the point of these tests. */
	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}
}
