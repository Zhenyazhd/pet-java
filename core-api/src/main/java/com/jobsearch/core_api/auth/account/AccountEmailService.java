package com.jobsearch.core_api.auth.account;

import com.jobsearch.core_api.auth.AppUser;
import com.jobsearch.core_api.auth.AppUserRepository;
import com.jobsearch.core_api.auth.account.AuthTokenService.Purpose;
import com.jobsearch.core_api.common.BadRequestException;
import com.jobsearch.core_api.common.Emails;
import com.jobsearch.core_api.common.FixedWindowRateLimiter;
import com.jobsearch.core_api.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Everything that goes through a mailed link: confirming an address and choosing a new password. The "send me a link"
 * calls never say whether the address has an account, and an address gets only a few mails per quarter of an hour, so
 * they cannot be used to find accounts or to flood someone's inbox.
 */
@Service
public class AccountEmailService {

	private static final String INVALID_LINK = "This link is invalid or has expired. Ask for a new one.";
	private static final int MAILS_PER_ADDRESS = 3;
	private static final Duration MAIL_WINDOW = Duration.ofMinutes(15);

	private final AppUserRepository userRepository;
	private final AuthTokenService tokens;
	private final MailService mail;
	private final PasswordEncoder passwordEncoder;
	private final FixedWindowRateLimiter rateLimiter;
	private final TransactionTemplate transaction;
	private final String baseUrl;

	public AccountEmailService(
			AppUserRepository userRepository,
			AuthTokenService tokens,
			MailService mail,
			PasswordEncoder passwordEncoder,
			FixedWindowRateLimiter rateLimiter,
			PlatformTransactionManager transactionManager,
			AppProperties appProperties
	) {
		this.userRepository = userRepository;
		this.tokens = tokens;
		this.mail = mail;
		this.passwordEncoder = passwordEncoder;
		this.rateLimiter = rateLimiter;
		this.transaction = new TransactionTemplate(transactionManager);
		this.baseUrl = appProperties.getPublicBaseUrl().replaceAll("/+$", "");
	}

	public void sendVerification(long userId, String email) {
		if (!mailAllowed(email)) {
			return;
		}
		mail.send(email, "Confirm your email address", """
				Welcome to Job Search.

				Confirm your email address to sign in:
				%s

				The link works once and expires in 24 hours. If you did not create this account, ignore this message.
				""".formatted(link("/verify-email", tokens.issue(userId, Purpose.VERIFY_EMAIL))));
	}

	/** Marks the address confirmed. The link is spent in the same transaction, so a failure leaves it usable. */
	public void verify(String token) {
		transaction.executeWithoutResult(status -> {
			AppUser user = userFor(token, Purpose.VERIFY_EMAIL);
			if (user.getEmailVerifiedAt() == null) {
				user.setEmailVerifiedAt(Instant.now());
			}
		});
	}

	/** Silent for an unknown or already confirmed address. */
	public void resendVerification(String email) {
		findByEmail(email).filter(user -> user.getEmailVerifiedAt() == null)
				.ifPresent(user -> sendVerification(user.getId(), user.getEmail()));
	}

	/** Silent for an unknown address. */
	public void forgotPassword(String email) {
		findByEmail(email).filter(user -> mailAllowed(user.getEmail())).ifPresent(user -> mail.send(
				user.getEmail(), "Reset your password", """
						Someone asked to reset the password of your Job Search account.

						Choose a new password:
						%s

						The link works once and expires in one hour. If it was not you, ignore this message: your password has not changed.
						""".formatted(link("/reset-password", tokens.issue(user.getId(), Purpose.RESET_PASSWORD)))));
	}

	/** Reaching the mailbox also proves the address, so a reset confirms it too. */
	public void resetPassword(String token, String newPassword) {
		// Slow on purpose, so it runs before the transaction opens and holds no connection while it does.
		String hash = passwordEncoder.encode(newPassword);
		transaction.executeWithoutResult(status -> {
			AppUser user = userFor(token, Purpose.RESET_PASSWORD);
			user.setPasswordHash(hash);
			if (user.getEmailVerifiedAt() == null) {
				user.setEmailVerifiedAt(Instant.now());
			}
		});
	}

	private AppUser userFor(String token, Purpose purpose) {
		return tokens.consume(token, purpose)
				.flatMap(userRepository::findById)
				.orElseThrow(() -> new BadRequestException(INVALID_LINK));
	}

	private Optional<AppUser> findByEmail(String email) {
		return userRepository.findByEmailIgnoreCase(Emails.normalize(email));
	}

	private boolean mailAllowed(String email) {
		// tryAcquire answers -1 when the call is allowed and the seconds to wait when it is not.
		return rateLimiter.tryAcquire("mail|" + Emails.normalize(email), MAILS_PER_ADDRESS, MAIL_WINDOW.toMillis()) < 0;
	}

	private String link(String path, String token) {
		return baseUrl + path + "?token=" + token;
	}
}
