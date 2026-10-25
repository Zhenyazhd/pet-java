package com.jobsearch.core_api.auth;

import com.jobsearch.core_api.auth.account.AccountEmailService;
import com.jobsearch.core_api.auth.invite.InviteCode;
import com.jobsearch.core_api.auth.invite.InviteCodeService;
import com.jobsearch.core_api.auth.AuthDtos.AuthUserResponse;
import com.jobsearch.core_api.auth.AuthDtos.DeleteAccountRequest;
import com.jobsearch.core_api.auth.AuthDtos.Features;
import com.jobsearch.core_api.auth.AuthDtos.LoginRequest;
import com.jobsearch.core_api.auth.AuthDtos.RegisterRequest;
import com.jobsearch.core_api.auth.AuthDtos.Registered;
import com.jobsearch.core_api.common.BadRequestException;
import com.jobsearch.core_api.common.Emails;
import com.jobsearch.core_api.common.FixedWindowRateLimiter;
import com.jobsearch.core_api.common.ForbiddenException;
import com.jobsearch.core_api.common.TooManyRequestsException;
import com.jobsearch.core_api.config.AppProperties;
import com.jobsearch.core_api.common.UnauthorizedException;
import com.jobsearch.core_api.common.UniqueConstraint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final AppUserRepository appUserRepository;
	private final CurrentUserService currentUserService;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final InviteCodeService inviteCodeService;
	private final AccountEmailService accountEmailService;
	private final SecurityContextRepository securityContextRepository;
	private final Features features;
	private final TransactionTemplate transaction;
	private final FixedWindowRateLimiter rateLimiter;
	private final int loginFailuresPerMinute;

	public AuthService(
			AppUserRepository appUserRepository,
			CurrentUserService currentUserService,
			PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager,
			InviteCodeService inviteCodeService,
			AccountEmailService accountEmailService,
			SecurityContextRepository securityContextRepository,
			AppProperties appProperties,
			PlatformTransactionManager transactionManager,
			FixedWindowRateLimiter rateLimiter
	) {
		this.appUserRepository = appUserRepository;
		this.currentUserService = currentUserService;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.inviteCodeService = inviteCodeService;
		this.accountEmailService = accountEmailService;
		this.securityContextRepository = securityContextRepository;
		this.features = new Features(appProperties.getAtsWorker().isEnabled(), appProperties.getAtsWorker().isEnabled());
		this.transaction = new TransactionTemplate(transactionManager);
		this.rateLimiter = rateLimiter;
		this.loginFailuresPerMinute = appProperties.getAuthRateLimit().getLoginPerMinute();
	}

	@Transactional(readOnly = true)
	public AuthUserResponse me() {
		AppUser user = currentUserService.requireUser();
		return AuthUserResponse.of(AppUserPrincipal.of(user), features);
	}

	public AuthUserResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		// Per account on top of the per-IP filter: guessing one password list from many addresses. Only failures count.
		String email = Emails.normalize(request.email());
		String failureKey = "login-failed|" + email;
		requireNotLocked(failureKey);
		try {
			Authentication authentication = authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(
							email,
							request.password()
					)
			);
			AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
			// After the password: only someone who knows it is told that the address is not confirmed yet.
			if (!principal.isEmailVerified()) {
				throw new ForbiddenException(ForbiddenException.EMAIL_NOT_VERIFIED,
						"Confirm your email address first. We sent you a link when you registered.");
			}
			establishSession(authentication, httpRequest, httpResponse);
			log.info("User logged in userId={}", principal.getId());
			return AuthUserResponse.of(principal, features);
		}
		catch (BadCredentialsException | DisabledException ex) {
			recordFailure(failureKey);
			throw new UnauthorizedException("Invalid email or password");
		}
	}

	/** Creates the account and mails the confirmation link; nobody is signed in until the address is confirmed. */
	public Registered register(RegisterRequest request) {
		// Invite first: a wrong code is refused before the slow hashing, so it cannot be used to burn CPU.
		inviteCodeService.requireAvailable(request.inviteCode());
		// Slow on purpose, so it runs before the transaction opens and holds no connection while it does.
		String passwordHash = passwordEncoder.encode(request.password());

		AppUserPrincipal principal = Objects.requireNonNull(transaction.execute(status ->
				createUser(request.inviteCode(), request.email(), request.displayName(), passwordHash, false)));
		log.info("Registered userId={}", principal.getId());
		accountEmailService.sendVerification(principal.getId(), principal.getUsername());
		return new Registered(principal.getUsername());
	}

	/**
	 * One transaction (the caller opens it): the invite is locked, the user created, the invite marked used. Any failure
	 * rolls all three back. {@code passwordHash} is null for someone who signs in with an outside provider.
	 */
	public AppUserPrincipal createUser(String inviteCode, String rawEmail, String rawDisplayName, String passwordHash, boolean verified) {
		InviteCode invite = inviteCodeService.lockAvailable(inviteCode);

		String email = Emails.normalize(rawEmail);
		String displayName = rawDisplayName == null || rawDisplayName.isBlank()
				? Emails.localPart(email)
				: rawDisplayName.strip();

		AppUser user = new AppUser();
		user.setEmail(email);
		user.setDisplayName(displayName);
		user.setPasswordHash(passwordHash);
		if (verified) {
			user.setEmailVerifiedAt(Instant.now());
		}
		AppUser saved = UniqueConstraint.onConflict(
				"Email already registered",
				() -> appUserRepository.saveAndFlush(user),
				UniqueConstraint.APP_USER_EMAIL_LOWER
		);

		inviteCodeService.markUsed(invite, saved.getId());

		return AppUserPrincipal.of(saved);
	}

	/** Signs in someone already vouched for, e.g. by an outside identity provider. */
	public AuthUserResponse signIn(AppUserPrincipal principal, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		establishSession(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()), httpRequest, httpResponse);
		log.info("User signed in userId={}", principal.getId());
		return AuthUserResponse.of(principal, features);
	}

	/**
	 * Removes the account and, through the foreign keys, everything it owned. Asks for the typed address and, when the
	 * account has a password, for that too; wrong passwords count against the same limit as failed logins.
	 */
	public void deleteAccount(DeleteAccountRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		AppUser user = currentUserService.requireUser();
		// AuthBootstrap would make the configured admin address an ADMIN again on the next start anyway.
		if (user.getRole() == UserRole.ADMIN) {
			throw new BadRequestException("The admin account is set in the configuration and cannot be deleted here.");
		}
		if (!Emails.normalize(request.email()).equals(user.getEmail())) {
			throw new BadRequestException("Type your email address exactly to confirm.");
		}
		if (user.getPasswordHash() != null) {
			String failureKey = "login-failed|" + user.getEmail();
			requireNotLocked(failureKey);
			if (request.password() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
				recordFailure(failureKey);
				throw new BadRequestException("Wrong password.");
			}
		}
		transaction.executeWithoutResult(status -> appUserRepository.deleteById(user.getId()));
		log.info("Account deleted userId={}", user.getId());
		logout(httpRequest, httpResponse);
	}

	public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		SecurityContextHolder.clearContext();
		HttpSession session = httpRequest.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		log.info("User logged out");
	}

	private void requireNotLocked(String failureKey) {
		long blockedFor = rateLimiter.retryAfterIfFull(failureKey, loginFailuresPerMinute);
		if (blockedFor > 0) {
			throw new TooManyRequestsException("Too many failed attempts — try again later", Duration.ofSeconds(blockedFor));
		}
	}

	private void recordFailure(String failureKey) {
		rateLimiter.tryAcquire(failureKey, loginFailuresPerMinute, Duration.ofMinutes(1).toMillis());
	}

	private void establishSession(
			Authentication authentication,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse
	) {
		// Rotate session id on login/register (session fixation defense).
		HttpSession existing = httpRequest.getSession(false);
		if (existing != null) {
			httpRequest.changeSessionId();
		}

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, httpRequest, httpResponse);
	}
}
