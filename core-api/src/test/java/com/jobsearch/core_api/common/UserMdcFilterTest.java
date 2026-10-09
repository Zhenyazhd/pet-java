package com.jobsearch.core_api.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jobsearch.core_api.auth.AppUserPrincipal;
import com.jobsearch.core_api.auth.UserRole;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** A web request's log lines say whose request it was, and the thread forgets it afterwards. */
class UserMdcFilterTest {

	@AfterEach
	void clear() {
		SecurityContextHolder.clearContext();
		MDC.clear();
	}

	@Test
	void theUserIdIsInTheLogContextWhileTheRequestRuns() throws Exception {
		AppUserPrincipal principal = new AppUserPrincipal(42L, "a@b.c", "A", "x", UserRole.USER);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
		AtomicReference<String> seen = new AtomicReference<>();

		new UserMdcFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
				new MockFilterChain() {
					@Override
					public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
						seen.set(MDC.get("userId"));
					}
				});

		assertEquals("42", seen.get());
		assertNull(MDC.get("userId"));
	}

	@Test
	void anAnonymousRequestAddsNothing() throws Exception {
		AtomicReference<String> seen = new AtomicReference<>("untouched");

		new UserMdcFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
				new MockFilterChain() {
					@Override
					public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
						seen.set(MDC.get("userId"));
					}
				});

		assertNull(seen.get());
	}
}
