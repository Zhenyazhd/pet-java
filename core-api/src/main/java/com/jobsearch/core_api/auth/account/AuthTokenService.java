package com.jobsearch.core_api.auth.account;

import com.jobsearch.core_api.common.Hashes;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time tokens that go out in mail links. Only the sha256 of a token is stored, so a copy of the table is no use to
 * anyone; a user has at most one live token per purpose, and asking for a new one replaces the old.
 */
@Service
public class AuthTokenService {

	public enum Purpose {
		VERIFY_EMAIL(Duration.ofHours(24)),
		RESET_PASSWORD(Duration.ofHours(1));

		private final Duration lifetime;

		Purpose(Duration lifetime) {
			this.lifetime = lifetime;
		}
	}

	private static final int TOKEN_BYTES = 32;

	private final JdbcClient jdbc;
	private final SecureRandom random = new SecureRandom();

	public AuthTokenService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** The new token, in the form that goes into the link. */
	@Transactional
	public String issue(long userId, Purpose purpose) {
		byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		jdbc.sql("""
				insert into auth_token (user_id, purpose, token_hash, expires_at)
				values (:userId, :purpose, :hash, now() + make_interval(secs => :seconds))
				on conflict (user_id, purpose) do update
					set token_hash = excluded.token_hash, expires_at = excluded.expires_at, used_at = null, created_at = now()
				""")
				.param("userId", userId)
				.param("purpose", purpose.name())
				.param("hash", Hashes.sha256(token))
				.param("seconds", purpose.lifetime.toSeconds())
				.update();
		return token;
	}

	/**
	 * The user the token belongs to, and the token is spent in the same statement: of two requests with one link only one
	 * gets an answer. Empty when the token is unknown, already used, expired, or made for another purpose.
	 */
	@Transactional
	public Optional<Long> consume(String token, Purpose purpose) {
		return jdbc.sql("""
				update auth_token set used_at = now()
				where token_hash = :hash and purpose = :purpose and used_at is null and expires_at > now()
				returning user_id
				""")
				.param("hash", Hashes.sha256(token))
				.param("purpose", purpose.name())
				.query(Long.class)
				.optional();
	}
}
