package com.jobsearch.core_api.profile;

import com.jobsearch.core_api.auth.AppUser;
import com.jobsearch.core_api.auth.AppUserRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.ai.ChatResult;
import com.jobsearch.core_api.ai.OpenRouterClient;
import com.jobsearch.core_api.ai.PromptPart;
import com.jobsearch.core_api.auth.UserRole;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The prompt-part endpoints as a signed-in browser sees them. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "app.auth-rate-limit.login-per-minute=1000")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PromptHttpTest {

	private static final String PASSWORD = "correct horse battery";
	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Value("${local.server.port}")
	private int port;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private JdbcTemplate jdbc;
	@MockitoBean
	private OpenRouterClient openRouterClient;

	/** One signed-in browser. */
	private final class Browser {
		private final HttpClient client = HttpClient.newHttpClient();
		private final Map<String, String> cookies = new LinkedHashMap<>();

		Browser() throws Exception {
			this(UserRole.ADMIN);
		}

		Browser(UserRole role) throws Exception {
			AppUser user = new AppUser();
			user.setRole(role);
			user.setEmail(UUID.randomUUID() + "@test.local");
			user.setDisplayName("Prompt Test");
			user.setPasswordHash(passwordEncoder.encode(PASSWORD));
			userRepository.save(user);
			send("GET", "/api/auth/csrf", null);
			HttpResponse<String> login = send("POST", "/api/auth/login",
					"{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}");
			assertEquals(200, login.statusCode(), login.body());
		}

		HttpResponse<String> send(String method, String path, String json) throws Exception {
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

		JsonNode item(String key) throws Exception {
			HttpResponse<String> list = send("GET", "/api/profile/prompts", null);
			assertEquals(200, list.statusCode(), list.body());
			for (JsonNode node : JSON.readTree(list.body())) {
				if (node.get("key").asString().equals(key)) {
					return node;
				}
			}
			throw new AssertionError("no item " + key);
		}

		HttpResponse<String> put(String key, String text) throws Exception {
			return send("PUT", "/api/profile/prompts/" + key, JSON.writeValueAsString(Map.of("text", text)));
		}
	}

	@Test
	void listsEveryPartAsDefaultAtFirst() throws Exception {
		Browser b = new Browser();

		JsonNode list = JSON.readTree(b.send("GET", "/api/profile/prompts", null).body());

		assertEquals(PromptPart.values().length, list.size());
		for (int i = 0; i < list.size(); i++) {
			PromptPart part = PromptPart.values()[i];
			JsonNode item = list.get(i);
			assertEquals(part.key(), item.get("key").asString());
			assertEquals(part.title(), item.get("title").asString());
			assertEquals(part.description(), item.get("description").asString());
			assertEquals(part.defaultText(), item.get("defaultText").asString());
			assertTrue(item.get("customText").isNull());
			assertFalse(item.get("customized").asBoolean());
			assertFalse(item.get("defaultChanged").asBoolean());
		}
	}

	@Test
	void saveReadAndResetRoundTrip() throws Exception {
		Browser b = new Browser();

		HttpResponse<String> saved = b.put("vacancy", "  Only reword; never add keywords.\r\nSecond line.  ");
		assertEquals(200, saved.statusCode(), saved.body());
		JsonNode body = JSON.readTree(saved.body());
		assertEquals("Only reword; never add keywords.\nSecond line.", body.get("customText").asString());
		assertTrue(body.get("customized").asBoolean());
		assertEquals("vacancy", body.get("key").asString());

		assertEquals(body.get("customText").asString(), b.item("vacancy").get("customText").asString());
		assertFalse(b.item("tailoring").get("customized").asBoolean());

		assertEquals(200, b.put("vacancy", "Changed again").statusCode());
		assertEquals("Changed again", b.item("vacancy").get("customText").asString());

		assertEquals(204, b.send("DELETE", "/api/profile/prompts/vacancy", null).statusCode());
		assertFalse(b.item("vacancy").get("customized").asBoolean());
		assertEquals(204, b.send("DELETE", "/api/profile/prompts/vacancy", null).statusCode());
	}

	@Test
	void textEqualToTheDefaultIsStoredAsNotCustomised() throws Exception {
		Browser b = new Browser();
		assertEquals(200, b.put("commitments", "something else").statusCode());

		String sameButCrlf = "\n" + PromptPart.COMMITMENTS.defaultText().replace("\n", "\r\n") + "\n\n";
		HttpResponse<String> response = b.put("commitments", sameButCrlf);

		assertEquals(200, response.statusCode(), response.body());
		JsonNode body = JSON.readTree(response.body());
		assertTrue(body.get("customText").isNull());
		assertFalse(body.get("customized").asBoolean());
		assertFalse(b.item("commitments").get("customized").asBoolean());
	}

	@Test
	void invalidTextIsRejected() throws Exception {
		Browser b = new Browser();

		assertEquals(400, b.put("vacancy", "   \n ").statusCode());
		assertEquals(400, b.put("vacancy", "x".repeat(6001)).statusCode());
		assertEquals(200, b.put("vacancy", "x".repeat(6000)).statusCode());
		assertEquals(400, b.put("vacancy", "bad\u0000text").statusCode());
		assertEquals(400, b.put("vacancy", "bad\u001btext").statusCode());
		assertEquals(200, b.put("vacancy", "tab\there\nand\rline").statusCode());
		assertEquals(400, b.send("PUT", "/api/profile/prompts/vacancy", "{}").statusCode());
	}

	@Test
	void unicodeRules() throws Exception {
		Browser b = new Browser();

		// A lone surrogate, written as a JSON escape (the client cannot send one as UTF-8).
		assertEquals(400, b.send("PUT", "/api/profile/prompts/vacancy", "{\"text\":\"a\\ud800b\"}").statusCode());
		assertEquals(400, b.send("PUT", "/api/profile/prompts/vacancy", "{\"text\":\"a\\udc00b\"}").statusCode());
		assertEquals(400, b.send("PUT", "/api/profile/prompts/vacancy", "{\"text\":\"a\\u2028b\"}").statusCode());
		assertEquals(400, b.send("PUT", "/api/profile/prompts/vacancy", "{\"text\":\"a\\u2029b\"}").statusCode());
		assertEquals(400, b.send("PUT", "/api/profile/prompts/vacancy", "{\"text\":\"a\\u0085b\"}").statusCode());
		// A family emoji joins people with zero-width joiners (category Cf) and must stay allowed.
		String family = "\uD83D\uDC69\u200D\uD83D\uDC69\u200D\uD83D\uDC67";
		HttpResponse<String> ok = b.put("vacancy", "Keep " + family + " as is");
		assertEquals(200, ok.statusCode(), ok.body());
		assertEquals("Keep " + family + " as is", JSON.readTree(ok.body()).get("customText").asString());
	}

	@Test
	void lengthIsCountedInUtf16Characters() throws Exception {
		Browser b = new Browser();
		// 3000 emoji = 6000 UTF-16 characters: still allowed; one more is not.
		assertEquals(200, b.put("vacancy", "\uD83D\uDE00".repeat(3000)).statusCode());
		HttpResponse<String> tooLong = b.put("vacancy", "\uD83D\uDE00".repeat(3000) + "x");
		assertEquals(400, tooLong.statusCode());
		assertTrue(tooLong.body().contains("UTF-16"), tooLong.body());
	}

	@Test
	void aHugeBodyIsRejectedBeforeAnythingElse() throws Exception {
		Browser b = new Browser();

		assertEquals(400, b.put("vacancy", "x".repeat(20_001)).statusCode());
	}

	@Test
	void allCustomisedPartsTogetherAreCapped() throws Exception {
		Browser b = new Browser();
		assertEquals(200, b.put("commitments", "a".repeat(6000)).statusCode());
		assertEquals(200, b.put("truthfulness", "b".repeat(6000)).statusCode());

		HttpResponse<String> over = b.put("vacancy", "c");

		assertEquals(400, over.statusCode(), over.body());
		assertTrue(over.body().contains("12000"), over.body());
		// Rewriting a part that already counts is judged without its old text.
		assertEquals(200, b.put("truthfulness", "b".repeat(5000)).statusCode());
		assertEquals(200, b.put("vacancy", "c".repeat(1000)).statusCode());
		assertEquals(400, b.put("vacancy", "c".repeat(1001)).statusCode());
		// Resetting one frees room.
		assertEquals(204, b.send("DELETE", "/api/profile/prompts/commitments", null).statusCode());
		assertEquals(200, b.put("tailoring", "d".repeat(6000)).statusCode());
	}

	@Test
	void anEditedDefaultIsReportedAsChangedUntilTheTextIsSavedAgain() throws Exception {
		Browser b = new Browser();
		HttpResponse<String> saved = b.put("vacancy", "My own wording");
		assertFalse(JSON.readTree(saved.body()).get("defaultChanged").asBoolean());
		assertFalse(b.item("vacancy").get("defaultChanged").asBoolean());

		// The shipped default changes later: the stored hash no longer matches it.
		jdbc.update("update user_prompt_part set base_hash = ? where part_key = 'vacancy'", "0".repeat(64));

		JsonNode item = b.item("vacancy");
		assertTrue(item.get("customized").asBoolean());
		assertTrue(item.get("defaultChanged").asBoolean());
		assertFalse(b.item("tailoring").get("defaultChanged").asBoolean());
		// Saving again acknowledges the new default.
		assertFalse(JSON.readTree(b.put("vacancy", "My own wording").body()).get("defaultChanged").asBoolean());
		assertFalse(b.item("vacancy").get("defaultChanged").asBoolean());
	}

	@Test
	void aRowWithAnUnknownKeyIsIgnored() throws Exception {
		Browser b = new Browser();
		assertEquals(200, b.put("vacancy", "Mine").statusCode());
		jdbc.update("insert into user_prompt_part (user_id, part_key, content, base_hash) "
				+ "select user_id, 'retired_part', 'old', repeat('0', 64) from user_prompt_part where part_key = 'vacancy'");

		JsonNode list = JSON.readTree(b.send("GET", "/api/profile/prompts", null).body());

		assertEquals(PromptPart.values().length, list.size());
		assertTrue(b.item("vacancy").get("customized").asBoolean());
		// The chat sends the user's own part and ignores the retired row.
		answerChat();
		assertEquals(200, b.send("POST", "/api/ai/resume/suggest",
				"{\"section\":\"profile\",\"instruction\":\"hello\"}").statusCode());
		assertFalse(lastSystemPrompt().contains("old\n"));
	}

	@Test
	void theChatSendsTheSavedCustomPartAndNothingOfTheDefault() throws Exception {
		Browser b = new Browser();
		assertEquals(200, b.put("truthfulness", "Only say what the user said, word for word.").statusCode());
		answerChat();

		HttpResponse<String> response = b.send("POST", "/api/ai/resume/suggest",
				"{\"section\":\"profile\",\"instruction\":\"hello\"}");

		assertEquals(200, response.statusCode(), response.body());
		String system = lastSystemPrompt();
		assertTrue(system.contains("\n\nOnly say what the user said, word for word.\n\n"), system);
		assertFalse(system.contains(PromptPart.TRUTHFULNESS.defaultText()));
		assertTrue(system.contains(PromptPart.COMMITMENTS.defaultText()));
		assertTrue(system.contains(PromptPart.TAILORING.defaultText()));
		// After a reset the default is back.
		assertEquals(204, b.send("DELETE", "/api/profile/prompts/truthfulness", null).statusCode());
		assertEquals(200, b.send("POST", "/api/ai/resume/suggest",
				"{\"section\":\"profile\",\"instruction\":\"hello\"}").statusCode());
		assertEquals(com.jobsearch.core_api.ai.SystemPromptComposer.defaultPrompt(), lastSystemPrompt());
	}

	private void answerChat() {
		org.mockito.Mockito.when(openRouterClient.complete(
				org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(new ChatResult.Answer("{\"message\":\"hi\",\"proposed\":null}"));
	}

	private String lastSystemPrompt() {
		org.mockito.ArgumentCaptor<String> system = org.mockito.ArgumentCaptor.forClass(String.class);
		org.mockito.Mockito.verify(openRouterClient, org.mockito.Mockito.atLeastOnce())
				.complete(system.capture(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
		return system.getValue();
	}

	@Test
	void unknownKeyIsNotFound() throws Exception {
		Browser b = new Browser();

		assertEquals(404, b.put("nope", "text").statusCode());
		assertEquals(404, b.send("DELETE", "/api/profile/prompts/nope", null).statusCode());
	}

	@Test
	void usersDoNotSeeEachOthersText() throws Exception {
		Browser a = new Browser();
		Browser other = new Browser();

		assertEquals(200, a.put("truthfulness", "Alice's wording").statusCode());

		assertFalse(other.item("truthfulness").get("customized").asBoolean());
		assertEquals(204, other.send("DELETE", "/api/profile/prompts/truthfulness", null).statusCode());
		assertEquals("Alice's wording", a.item("truthfulness").get("customText").asString());
	}

	@Test
	void anOrdinaryUserCannotReadEditOrPublishThePrompts() throws Exception {
		Browser user = new Browser(UserRole.USER);

		assertEquals(403, user.send("GET", "/api/profile/prompts", null).statusCode());
		assertEquals(403, user.put("vacancy", "mine").statusCode());
		assertEquals(403, user.send("DELETE", "/api/profile/prompts/vacancy", null).statusCode());
		assertEquals(403, user.send("POST", "/api/profile/prompts/publish", null).statusCode());
	}

	@Test
	void publishingMakesTheAdminsWordingTheDefaultForEveryone() throws Exception {
		Browser admin = new Browser();
		Browser user = new Browser(UserRole.USER);
		assertEquals(200, admin.put("truthfulness", "Published wording for all.").statusCode());
		try {
			HttpResponse<String> published = admin.send("POST", "/api/profile/prompts/publish", null);
			assertEquals(200, published.statusCode(), published.body());

			// The admin now sees it as the default, not as their own text.
			JsonNode item = admin.item("truthfulness");
			assertEquals("Published wording for all.", item.get("defaultText").asString());
			assertFalse(item.get("customized").asBoolean());
			// The other parts stay as shipped.
			assertEquals(PromptPart.COMMITMENTS.defaultText(), admin.item("commitments").get("defaultText").asString());

			// An ordinary user's chat uses it.
			answerChat();
			assertEquals(200, user.send("POST", "/api/ai/resume/suggest",
					"{\"section\":\"profile\",\"instruction\":\"hello\"}").statusCode());
			assertTrue(lastSystemPrompt().contains("\n\nPublished wording for all.\n\n"));
			assertFalse(lastSystemPrompt().contains(PromptPart.TRUTHFULNESS.defaultText()));

			// Publishing again with nothing customised puts the shipped text back for everyone.
			assertEquals(200, admin.send("POST", "/api/profile/prompts/publish", null).statusCode());
			assertEquals(PromptPart.TRUTHFULNESS.defaultText(), admin.item("truthfulness").get("defaultText").asString());
			assertEquals(200, user.send("POST", "/api/ai/resume/suggest",
					"{\"section\":\"profile\",\"instruction\":\"hello\"}").statusCode());
			assertEquals(com.jobsearch.core_api.ai.SystemPromptComposer.defaultPrompt(), lastSystemPrompt());
		}
		finally {
			jdbc.update("delete from prompt_default");
		}
	}

	@Test
	void anOrdinaryUsersOldOwnTextIsNotUsed() throws Exception {
		Browser user = new Browser(UserRole.USER);
		jdbc.update("insert into user_prompt_part (user_id, part_key, content, base_hash) "
				+ "select id, 'vacancy', 'Left over from before', repeat('0', 64) from app_user order by id desc limit 1");
		answerChat();

		assertEquals(200, user.send("POST", "/api/ai/resume/suggest",
				"{\"section\":\"profile\",\"instruction\":\"hello\"}").statusCode());

		assertFalse(lastSystemPrompt().contains("Left over from before"));
	}

	@Test
	void signedOutRequestsAreRefused() throws Exception {
		HttpResponse<String> response = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/profile/prompts")).build(),
				HttpResponse.BodyHandlers.ofString());

		assertEquals(401, response.statusCode());
	}
}
