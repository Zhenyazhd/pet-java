package com.jobsearch.core_api.profile;

import com.jobsearch.core_api.auth.AppUser;
import com.jobsearch.core_api.auth.AppUserRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jobsearch.core_api.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

/** The Career path accepts exactly up to 40,000 characters. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "app.auth-rate-limit.login-per-minute=1000")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CareerPathLimitTest {

	private static final String PASSWORD = "correct horse battery";
	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Value("${local.server.port}")
	private int port;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;

	private final HttpClient client = HttpClient.newHttpClient();
	private final Map<String, String> cookies = new LinkedHashMap<>();
	private String email;

	@Test
	void fortyThousandCharactersAreAccepted() throws Exception {
		signIn();
		String career = "a".repeat(40_000);

		HttpResponse<String> saved = send("PUT", "/api/profile", profileJson(career));

		assertEquals(200, saved.statusCode(), saved.body());
		HttpResponse<String> read = send("GET", "/api/profile", null);
		assertEquals(40_000, JSON.readTree(read.body()).get("careerPath").asString().length());
	}

	@Test
	void oneCharacterMoreIsRejected() throws Exception {
		signIn();

		HttpResponse<String> saved = send("PUT", "/api/profile", profileJson("a".repeat(40_001)));

		assertEquals(400, saved.statusCode(), saved.body());
	}

	private String profileJson(String career) {
		return JSON.createObjectNode().put("displayName", "Career Test").put("email", email).put("careerPath", career).toString();
	}

	private void signIn() throws Exception {
		AppUser user = new AppUser();
		user.setEmail(UUID.randomUUID() + "@test.local");
		user.setDisplayName("Career Test");
		user.setPasswordHash(passwordEncoder.encode(PASSWORD));
		userRepository.save(user);
		email = user.getEmail();
		send("GET", "/api/auth/csrf", null);
		HttpResponse<String> login = send("POST", "/api/auth/login",
				"{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");
		assertEquals(200, login.statusCode(), login.body());
	}

	private HttpResponse<String> send(String method, String path, String json) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
		if (!cookies.isEmpty()) {
			builder.header("Cookie", String.join("; ",
					cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList()));
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
