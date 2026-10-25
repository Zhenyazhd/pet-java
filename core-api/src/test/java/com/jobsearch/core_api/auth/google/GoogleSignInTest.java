package com.jobsearch.core_api.auth.google;

import com.jobsearch.core_api.auth.AppUser;
import com.jobsearch.core_api.auth.AppUserPrincipal;
import com.jobsearch.core_api.auth.AppUserRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.auth.google.GoogleLoginHandler.Profile;
import com.jobsearch.core_api.common.UnauthorizedException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.ActiveProfiles;

/** Signing in with Google: who gets in, who is linked to whom, and the one case where a password is taken away. */
@SpringBootTest(properties = {
		"app.google.client-id=test-client-id",
		"app.google.client-secret=test-client-secret-0123456789",
		"app.public-base-url=https://app.example.test/"
})
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class GoogleSignInTest {

	@Autowired
	private GoogleLoginHandler handler;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void aNewPersonNeedsAnInviteAndGetsAConfirmedAccountWithoutAPassword() {
		String invite = newInvite();
		Profile profile = profile("new-" + UUID.randomUUID() + "@test.local");

		AppUserPrincipal created = handler.resolve(profile, invite);

		AppUser user = userRepository.findById(created.getId()).orElseThrow();
		assertNotNull(user.getEmailVerifiedAt());
		assertNull(user.getPasswordHash(), "nobody can sign in to this account with a password until one is set by a reset");
		assertEquals(1, jdbc.queryForObject("select count(*) from invite_code where code = ? and used_at is not null", Integer.class, invite));
		assertEquals(created.getId(), handler.resolve(profile, null).getId(), "the next visit needs no invite");
	}

	@Test
	void aNewPersonWithoutAnInviteOrWithABadOneCreatesNothing() {
		Profile profile = profile("stranger-" + UUID.randomUUID() + "@test.local");

		GoogleSignInException missing = assertThrows(GoogleSignInException.class, () -> handler.resolve(profile, " "));
		assertEquals(GoogleSignInException.INVITE_REQUIRED, missing.reason());
		assertThrows(UnauthorizedException.class, () -> handler.resolve(profile, "NO-SUCH-INVITE"));

		assertEquals(0, jdbc.queryForObject("select count(*) from app_user where email = ?", Integer.class, profile.email()));
	}

	@Test
	void anAddressGoogleHasNotConfirmedIsRefused() {
		Profile unverified = new Profile("sub-" + UUID.randomUUID(), "x-" + UUID.randomUUID() + "@test.local", false, "X");

		GoogleSignInException refused = assertThrows(GoogleSignInException.class, () -> handler.resolve(unverified, newInvite()));

		assertEquals(GoogleSignInException.UNVERIFIED, refused.reason());
	}

	@Test
	void anExistingConfirmedAccountIsLinkedByItsAddressWithoutAnInviteAndKeepsItsPassword() {
		AppUser user = localUser("kept-" + UUID.randomUUID() + "@test.local", true);

		AppUserPrincipal signedIn = handler.resolve(profile(user.getEmail().toUpperCase()), null);

		assertEquals(user.getId(), signedIn.getId());
		assertEquals("local-password-hash", userRepository.findById(user.getId()).orElseThrow().getPasswordHash());
	}

	@Test
	void anUnconfirmedAccountWithTheSameAddressLosesItsPasswordWhenTheRealOwnerArrivesFromGoogle() {
		AppUser squatter = localUser("owner-" + UUID.randomUUID() + "@test.local", false);

		handler.resolve(profile(squatter.getEmail()), null);

		AppUser after = userRepository.findById(squatter.getId()).orElseThrow();
		assertNotNull(after.getEmailVerifiedAt());
		assertNull(after.getPasswordHash(), "whoever registered the address first can no longer sign in with their password");
	}

	@Test
	void theHandlerOpensTheSessionAndSendsTheBrowserBackToTheApp() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.getSession(true).setAttribute(GoogleLoginHandler.INVITE_ATTRIBUTE, newInvite());
		MockHttpServletResponse response = new MockHttpServletResponse();
		String email = "handler-" + UUID.randomUUID() + "@test.local";

		handler.onAuthenticationSuccess(request, response, token(profile(email)));

		assertEquals("https://app.example.test/", response.getRedirectedUrl());
		assertNull(request.getSession(false).getAttribute(GoogleLoginHandler.INVITE_ATTRIBUTE), "the invite is not left behind");
		assertNotNull(request.getSession(false).getAttribute("SPRING_SECURITY_CONTEXT"));
		assertTrue(userRepository.findByEmailIgnoreCase(email).isPresent());
	}

	@Test
	void theHandlerSendsAnUninvitedNewcomerBackToSignInWithAReason() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.getSession(true);
		MockHttpServletResponse response = new MockHttpServletResponse();
		String email = "uninvited-" + UUID.randomUUID() + "@test.local";

		handler.onAuthenticationSuccess(request, response, token(profile(email)));

		assertEquals("https://app.example.test/login?google=invite_required", response.getRedirectedUrl());
		assertFalse(userRepository.findByEmailIgnoreCase(email).isPresent());
		assertNull(request.getSession(false).getAttribute("SPRING_SECURITY_CONTEXT"), "nobody was signed in");
	}

	private static Profile profile(String email) {
		return new Profile("sub-" + UUID.randomUUID(), email, true, "Google Person");
	}

	private AppUser localUser(String email, boolean confirmed) {
		AppUser user = new AppUser();
		user.setEmail(email);
		user.setDisplayName("Local");
		user.setPasswordHash("local-password-hash");
		if (confirmed) {
			user.setEmailVerifiedAt(Instant.now());
		}
		return userRepository.save(user);
	}

	private String newInvite() {
		String code = "INVITE-" + UUID.randomUUID().toString().toUpperCase();
		jdbc.update("insert into invite_code (code, created_at) values (?, now())", code);
		return code;
	}

	private static OAuth2AuthenticationToken token(Profile profile) {
		OidcIdToken idToken = OidcIdToken.withTokenValue("token")
				.subject(profile.subject())
				.claim("email", profile.email())
				.claim("email_verified", profile.emailVerified())
				.claim("name", profile.name())
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(60))
				.build();
		return new OAuth2AuthenticationToken(new DefaultOidcUser(List.of(), idToken), List.of(), "google");
	}
}
