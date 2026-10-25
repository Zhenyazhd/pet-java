package com.jobsearch.core_api.auth.account;

import com.jobsearch.core_api.auth.AppUserRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobsearch.core_api.TestSupport;
import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.auth.account.AuthTokenService.Purpose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** The links in account mails: single use, short life, one live token per user and purpose, nothing readable stored. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthTokenServiceTest {

	@Autowired
	private AuthTokenService tokens;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void aTokenWorksOnceAndOnlyForItsPurpose() {
		long userId = TestSupport.newUser(userRepository);
		String token = tokens.issue(userId, Purpose.VERIFY_EMAIL);

		assertTrue(tokens.consume(token, Purpose.RESET_PASSWORD).isEmpty(), "another purpose does not open it");
		assertEquals(userId, tokens.consume(token, Purpose.VERIFY_EMAIL).orElseThrow());
		assertTrue(tokens.consume(token, Purpose.VERIFY_EMAIL).isEmpty(), "and it is spent");
	}

	@Test
	void anExpiredOrUnknownTokenIsRefused() {
		long userId = TestSupport.newUser(userRepository);
		String token = tokens.issue(userId, Purpose.RESET_PASSWORD);
		jdbc.update("update auth_token set expires_at = now() - interval '1 second' where user_id = ?", userId);

		assertTrue(tokens.consume(token, Purpose.RESET_PASSWORD).isEmpty());
		assertTrue(tokens.consume("not-a-token", Purpose.RESET_PASSWORD).isEmpty());
	}

	@Test
	void askingAgainReplacesTheOldTokenAndKeepsOneRowPerPurpose() {
		long userId = TestSupport.newUser(userRepository);
		String first = tokens.issue(userId, Purpose.VERIFY_EMAIL);
		String second = tokens.issue(userId, Purpose.VERIFY_EMAIL);
		tokens.issue(userId, Purpose.RESET_PASSWORD);

		assertNotEquals(first, second);
		assertTrue(tokens.consume(first, Purpose.VERIFY_EMAIL).isEmpty(), "the old link stops working");
		assertEquals(userId, tokens.consume(second, Purpose.VERIFY_EMAIL).orElseThrow());
		assertEquals(2, jdbc.queryForObject("select count(*) from auth_token where user_id = ?", Integer.class, userId));
	}

	@Test
	void aSpentTokenIsLiveAgainAfterANewIssue() {
		long userId = TestSupport.newUser(userRepository);
		String spent = tokens.issue(userId, Purpose.RESET_PASSWORD);
		tokens.consume(spent, Purpose.RESET_PASSWORD);

		String fresh = tokens.issue(userId, Purpose.RESET_PASSWORD);

		assertEquals(userId, tokens.consume(fresh, Purpose.RESET_PASSWORD).orElseThrow());
	}

	@Test
	void theTokenItselfIsNotStored() {
		long userId = TestSupport.newUser(userRepository);
		String token = tokens.issue(userId, Purpose.VERIFY_EMAIL);

		String stored = jdbc.queryForObject("select token_hash from auth_token where user_id = ?", String.class, userId);

		assertFalse(stored.contains(token));
		assertEquals(64, stored.length());
	}
}
