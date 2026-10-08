package com.jobsearch.core_api.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.profile.AppUser;
import com.jobsearch.core_api.profile.AppUserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/** The resume endpoints as a signed-in browser sees them: real login, session and CSRF, over HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ResumeHttpTest {

	private static final String PASSWORD = "correct horse battery";
	private static final Pattern CYRILLIC = Pattern.compile("\\p{IsCyrillic}");

	@Value("${local.server.port}")
	private int port;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private JdbcTemplate jdbc;

	private final HttpClient client = HttpClient.newHttpClient();
	/** What a browser keeps: the cookies of every response, sent back on the next request. */
	private final Map<String, String> cookies = new LinkedHashMap<>();
	private long userId;

	@BeforeEach
	void signIn() throws Exception {
		AppUser user = new AppUser();
		user.setEmail(UUID.randomUUID() + "@test.local");
		user.setDisplayName("Http Test");
		user.setPasswordHash(passwordEncoder.encode(PASSWORD));
		userId = userRepository.save(user).getId();

		send("GET", "/api/auth/csrf", null, null);
		HttpResponse<String> login = send("POST", "/api/auth/login",
				"{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}", null);
		assertEquals(200, login.statusCode(), login.body());
	}

	@Test
	void aNullJobInTheResumeIsRejectedBeforeItIsStored() throws Exception {
		HttpResponse<String> response = send("PUT", "/api/resume", resumeJson("[null]", "[]"), null);

		assertEquals(400, response.statusCode(), response.body());
	}

	@Test
	void aNullBulletIsRejectedToo() throws Exception {
		String job = "{\"title\":\"Engineer\",\"subtitle\":\"\",\"dates\":\"\",\"bullets\":[null]}";

		assertEquals(400, send("PUT", "/api/resume", resumeJson("[" + job + "]", "[]"), null).statusCode());
	}

	@Test
	void validationMessagesAreEnglishWhateverTheBrowserLanguageIs() throws Exception {
		HttpResponse<String> response = send("PUT", "/api/resume", resumeJson("[null]", "[]"), "ru-RU,ru;q=0.9");

		assertEquals(400, response.statusCode());
		assertFalse(CYRILLIC.matcher(response.body()).find(), response.body());
	}

	@Test
	void theEditorLoadsADamagedResumeButCompilingRefusesIt() throws Exception {
		jdbc.update("update app_user set resume_json = '0', resume_version = 5 where id = ?", userId);

		HttpResponse<String> editor = send("GET", "/api/resume", null, null);
		HttpResponse<String> compile = send("POST", "/api/resume/compile", "{}", null);

		assertEquals(200, editor.statusCode(), editor.body());
		assertTrue(editor.body().contains("\"version\":5"), editor.body());
		assertEquals(409, compile.statusCode(), compile.body());
		assertTrue(compile.body().contains("damaged"), compile.body());
	}

	private static String resumeJson(String experience, String education) {
		return "{\"name\":\"Ada\",\"headline\":\"\",\"phone\":\"\",\"email\":\"\",\"linkedinUrl\":\"\",\"linkedinLabel\":\"\","
				+ "\"profile\":\"\",\"experience\":" + experience + ",\"education\":" + education
				+ ",\"achievements\":[],\"skills\":[],\"locale\":\"en\",\"version\":0}";
	}

	private HttpResponse<String> send(String method, String path, String json, String acceptLanguage) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
		if (!cookies.isEmpty()) {
			builder.header("Cookie", String.join("; ", cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList()));
		}
		if (cookies.containsKey("XSRF-TOKEN")) {
			builder.header("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
		}
		if (acceptLanguage != null) {
			builder.header("Accept-Language", acceptLanguage);
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
