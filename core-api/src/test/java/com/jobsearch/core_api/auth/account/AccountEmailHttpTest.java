package com.jobsearch.core_api.auth.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.common.FixedWindowRateLimiter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Confirming an address and resetting a password through the mailed links, over real HTTP. */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "app.auth-rate-limit.register-per-minute=1000")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AccountEmailHttpTest {

	private static final String PASSWORD = "correct horse battery";
	private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

	@Value("${local.server.port}")
	private int port;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private FixedWindowRateLimiter rateLimiter;
	@MockitoBean
	private MailService mail;

	private final HttpClient client = HttpClient.newHttpClient();
	private final Map<String, String> cookies = new LinkedHashMap<>();

	@BeforeEach
	void fetchCsrfToken() throws Exception {
		rateLimiter.clear();
		cookies.clear();
		clearInvocations(mail);
		send("GET", "/api/auth/csrf", null);
	}

	@Test
	void theMailedLinkConfirmsTheAddressOnceAndThenTheUserSignsIn() throws Exception {
		String email = registered();
		String token = lastMailedToken(email);

		HttpResponse<String> early = send("POST", "/api/auth/login", credentials(email, PASSWORD));
		assertEquals(403, early.statusCode());
		assertTrue(early.body().contains("\"code\":\"email_not_verified\""), early.body());
		assertEquals(204, send("POST", "/api/auth/verify-email", body("token", token)).statusCode());
		assertEquals(200, send("POST", "/api/auth/login", credentials(email, PASSWORD)).statusCode());
		assertEquals(400, send("POST", "/api/auth/verify-email", body("token", token)).statusCode(), "a link works once");
	}

	@Test
	void googleIsOffUnlessAClientIsConfigured() throws Exception {
		HttpResponse<String> config = send("GET", "/api/auth/config", null);

		assertEquals(200, config.statusCode());
		assertTrue(config.body().contains("\"google\":false"), config.body());
		assertEquals(404, send("POST", "/api/auth/google/start", "{\"inviteCode\":\"X\"}").statusCode());
		assertEquals(404, send("GET", "/oauth2/authorization/google", null).statusCode());
	}

	@Test
	void aWrongPasswordIsNeverToldThatTheAddressIsUnconfirmed() throws Exception {
		String email = registered();

		assertEquals(401, send("POST", "/api/auth/login", credentials(email, "not the password")).statusCode());
	}

	@Test
	void anUnknownOrExpiredLinkIsRefused() throws Exception {
		String email = registered();
		String token = lastMailedToken(email);
		jdbc.update("update auth_token set expires_at = now() - interval '1 second' "
				+ "where user_id = (select id from app_user where email = ?)", email);

		assertEquals(400, send("POST", "/api/auth/verify-email", body("token", token)).statusCode());
		assertEquals(400, send("POST", "/api/auth/verify-email", body("token", "nope")).statusCode());
	}

	@Test
	void resendingMailsANewLinkAndTheOldOneStopsWorking() throws Exception {
		String email = registered();
		String first = lastMailedToken(email);
		clearInvocations(mail);

		assertEquals(202, send("POST", "/api/auth/resend-verification", body("email", email)).statusCode());
		String second = lastMailedToken(email);

		assertFalse(first.equals(second));
		assertEquals(400, send("POST", "/api/auth/verify-email", body("token", first)).statusCode());
		assertEquals(204, send("POST", "/api/auth/verify-email", body("token", second)).statusCode());
	}

	@Test
	void theSendMeALinkCallsAnswerTheSameForAnAddressWithNoAccount() throws Exception {
		String stranger = "nobody-" + UUID.randomUUID() + "@test.local";

		assertEquals(202, send("POST", "/api/auth/resend-verification", body("email", stranger)).statusCode());
		assertEquals(202, send("POST", "/api/auth/forgot-password", body("email", stranger)).statusCode());

		verify(mail, never()).send(anyString(), anyString(), anyString());
	}

	@Test
	void anAddressGetsOnlyAFewMailsInAWhile() throws Exception {
		String email = registered();
		clearInvocations(mail);

		for (int i = 0; i < 6; i++) {
			assertEquals(202, send("POST", "/api/auth/resend-verification", body("email", email)).statusCode());
		}

		// The mail sent at registration used one of the three.
		verify(mail, org.mockito.Mockito.times(2)).send(anyString(), anyString(), anyString());
	}

	@Test
	void aPasswordCanBeResetOnceThroughTheMailedLinkAndThatAlsoConfirmsTheAddress() throws Exception {
		String email = registered();
		clearInvocations(mail);

		assertEquals(202, send("POST", "/api/auth/forgot-password", body("email", email)).statusCode());
		String token = lastMailedToken(email);
		String newPassword = "a brand new passphrase";

		assertEquals(204, send("POST", "/api/auth/reset-password", reset(token, newPassword)).statusCode());
		assertEquals(200, send("POST", "/api/auth/login", credentials(email, newPassword)).statusCode());
		assertEquals(401, send("POST", "/api/auth/login", credentials(email, PASSWORD)).statusCode());
		assertEquals(400, send("POST", "/api/auth/reset-password", reset(token, "yet another passphrase")).statusCode());
	}

	@Test
	void aResetLinkDoesNotConfirmWithTheWrongPurposeAndTheNewPasswordIsChecked() throws Exception {
		String email = registered();
		String verifyToken = lastMailedToken(email);

		assertEquals(400, send("POST", "/api/auth/reset-password", reset(verifyToken, "a brand new passphrase")).statusCode(),
				"a confirmation link does not reset a password");
		assertEquals(400, send("POST", "/api/auth/reset-password", reset(verifyToken, "short")).statusCode());
		assertEquals(204, send("POST", "/api/auth/verify-email", body("token", verifyToken)).statusCode(), "and it was not spent by that");
	}

	/** Registers a new account and returns its address; the confirmation mail is the latest on the mock. */
	private String registered() throws Exception {
		String invite = "INVITE-" + UUID.randomUUID().toString().toUpperCase();
		jdbc.update("insert into invite_code (code, created_at) values (?, now())", invite);
		String email = "acct-" + UUID.randomUUID() + "@test.local";
		HttpResponse<String> response = send("POST", "/api/auth/register",
				"{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"inviteCode\":\"" + invite + "\"}");
		assertEquals(201, response.statusCode(), response.body());
		return email;
	}

	/** The token in the latest mail sent to the address. */
	private String lastMailedToken(String email) {
		ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
		verify(mail, atLeastOnce()).send(org.mockito.ArgumentMatchers.eq(email), anyString(), text.capture());
		Matcher matcher = TOKEN.matcher(text.getValue());
		assertTrue(matcher.find(), text.getValue());
		String token = matcher.group(1);
		assertNotNull(token);
		return token;
	}

	private static String credentials(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

	private static String reset(String token, String password) {
		return "{\"token\":\"" + token + "\",\"password\":\"" + password + "\"}";
	}

	private static String body(String key, String value) {
		return "{\"" + key + "\":\"" + value + "\"}";
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
