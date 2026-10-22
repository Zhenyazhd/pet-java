package com.jobsearch.core_api.jobsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jobsearch.core_api.auth.AppUser;
import com.jobsearch.core_api.auth.AppUserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;

/** One signed-in browser: real login, session cookie and CSRF header over HTTP. */
final class Browser {

	private static final String PASSWORD = "correct horse battery";

	private final int port;
	private final HttpClient client = HttpClient.newHttpClient();
	private final Map<String, String> cookies = new LinkedHashMap<>();
	final long userId;

	Browser(int port, AppUserRepository users, PasswordEncoder encoder, boolean signIn) throws Exception {
		this.port = port;
		AppUser user = new AppUser();
		user.setEmail(UUID.randomUUID() + "@test.local");
		user.setDisplayName("Job Search Test");
		user.setPasswordHash(encoder.encode(PASSWORD));
		userId = users.save(user).getId();
		send("GET", "/api/auth/csrf", null);
		if (signIn) {
			HttpResponse<String> login = send("POST", "/api/auth/login",
					"{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}");
			assertEquals(200, login.statusCode(), login.body());
		}
	}

	HttpResponse<String> send(String method, String path, String json) throws Exception {
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
