package com.jobsearch.core_api.auth.google;

import com.jobsearch.core_api.auth.AppUser;
import com.jobsearch.core_api.auth.AppUserPrincipal;
import com.jobsearch.core_api.auth.AppUserRepository;
import com.jobsearch.core_api.auth.AuthController;
import com.jobsearch.core_api.auth.AuthService;
import com.jobsearch.core_api.common.Emails;
import com.jobsearch.core_api.common.UnauthorizedException;
import com.jobsearch.core_api.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Where Spring's Google login ends: finds or creates the account of the person Google vouched for, opens the same session
 * a password sign-in does, and sends the browser back to a page of the web app (never to an address taken from the request).
 * A person is known by Google's stable id (the "sub"), not by their email alone: an address can change hands, and Google
 * only vouches for it as of today.
 */
@Component
@ConditionalOnExpression(GoogleOAuthConfig.ENABLED)
public class GoogleLoginHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

	/** The invite code a new person gave before leaving for Google (see {@code AuthController.googleStart}). */
	public static final String INVITE_ATTRIBUTE = "google.invite";
	static final String PROVIDER = "google";

	/** What Google told us about the person who just signed in. */
	record Profile(String subject, String email, boolean emailVerified, String name) {
	}

	private static final Logger log = LoggerFactory.getLogger(GoogleLoginHandler.class);

	private final AppUserRepository userRepository;
	private final AuthService authService;
	private final JdbcClient jdbc;
	private final TransactionTemplate transaction;
	private final String baseUrl;

	GoogleLoginHandler(
			AppUserRepository userRepository,
			AuthService authService,
			JdbcClient jdbc,
			PlatformTransactionManager transactionManager,
			AppProperties appProperties
	) {
		this.userRepository = userRepository;
		this.authService = authService;
		this.jdbc = jdbc;
		this.transaction = new TransactionTemplate(transactionManager);
		this.baseUrl = appProperties.getPublicBaseUrl().replaceAll("/+$", "");
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
			throws IOException {
		OidcUser google = (OidcUser) authentication.getPrincipal();
		String invite = takeInvite(request);
		try {
			AppUserPrincipal principal = resolve(
					new Profile(google.getSubject(), google.getEmail(), Boolean.TRUE.equals(google.getEmailVerified()), google.getFullName()),
					invite);
			authService.signIn(principal, request, response);
			response.sendRedirect(baseUrl + "/");
		}
		catch (GoogleSignInException ex) {
			log.info("Google sign-in refused reason={}", ex.reason());
			redirectToSignIn(response, ex.reason());
		}
		catch (UnauthorizedException ex) {
			log.info("Google sign-in refused: invite code");
			redirectToSignIn(response, GoogleSignInException.INVITE_INVALID);
		}
		catch (RuntimeException ex) {
			log.error("Google sign-in failed", ex);
			redirectToSignIn(response, GoogleSignInException.FAILED);
		}
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
			throws IOException {
		log.info("Google sign-in did not complete: {}", exception.getClass().getSimpleName());
		redirectToSignIn(response, GoogleSignInException.FAILED);
	}

	/** Read once: an invite code given for one attempt is not left in the session for the next. */
	private static String takeInvite(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null) {
			return null;
		}
		Object invite = session.getAttribute(INVITE_ATTRIBUTE);
		session.removeAttribute(INVITE_ATTRIBUTE);
		return invite instanceof String code ? code : null;
	}

	private void redirectToSignIn(HttpServletResponse response, String reason) throws IOException {
		response.sendRedirect(baseUrl + "/login?google=" + reason);
	}

	/**
	 * The account for this person: the one already linked to their Google id; else the one with their address, which is
	 * linked now; else a new one, which needs an unused invite code. A bad or missing invite fails with
	 * {@link GoogleSignInException} or {@link UnauthorizedException} and creates nothing.
	 */
	AppUserPrincipal resolve(Profile profile, String inviteCode) {
		if (!profile.emailVerified() || profile.email() == null || profile.email().isBlank()) {
			throw new GoogleSignInException(GoogleSignInException.UNVERIFIED);
		}
		return Objects.requireNonNull(transaction.execute(status -> {
			Optional<AppUser> linked = linkedUser(profile.subject());
			if (linked.isPresent()) {
				return AppUserPrincipal.of(linked.get());
			}
			Optional<AppUser> existing = userRepository.findByEmailIgnoreCase(Emails.normalize(profile.email()));
			if (existing.isPresent()) {
				AppUser user = existing.get();
				if (user.getEmailVerifiedAt() == null) {
					// Whoever registered this address never proved it was theirs, and Google now has: the password they
					// chose must not survive, or a stranger who registered first would keep a way into the real owner's account.
					user.setEmailVerifiedAt(Instant.now());
					user.setPasswordHash(null);
				}
				link(user.getId(), profile.subject());
				return AppUserPrincipal.of(user);
			}
			if (inviteCode == null || inviteCode.isBlank()) {
				throw new GoogleSignInException(GoogleSignInException.INVITE_REQUIRED);
			}
			AppUserPrincipal created = authService.createUser(inviteCode, profile.email(), profile.name(), null, true);
			link(created.getId(), profile.subject());
			return created;
		}));
	}

	private Optional<AppUser> linkedUser(String subject) {
		return jdbc.sql("select user_id from oauth_identity where provider = :provider and subject = :subject")
				.param("provider", PROVIDER)
				.param("subject", subject)
				.query(Long.class)
				.optional()
				.flatMap(userRepository::findById);
	}

	private void link(long userId, String subject) {
		jdbc.sql("insert into oauth_identity (provider, subject, user_id) values (:provider, :subject, :userId)")
				.param("provider", PROVIDER)
				.param("subject", subject)
				.param("userId", userId)
				.update();
	}
}
