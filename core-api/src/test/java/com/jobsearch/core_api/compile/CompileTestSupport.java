package com.jobsearch.core_api.compile;

import com.jobsearch.core_api.auth.AppUserPrincipal;
import com.jobsearch.core_api.auth.UserRole;
import com.jobsearch.core_api.profile.AppUser;
import com.jobsearch.core_api.profile.AppUserRepository;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

final class CompileTestSupport {

	private CompileTestSupport() {
	}

	/**
	 * Every new user has the same default resume and so the same LaTeX hash: a PDF cached by one
	 * test would turn the next test's enqueue into a cache hit.
	 */
	static void clearQueueAndCache(CompileJobRepository jobRepository, ResumePdfCacheRepository cacheRepository) {
		jobRepository.deleteAll();
		cacheRepository.deleteAll();
	}

	/** Creates a user and makes it the authenticated user of the current thread; returns its id. */
	static long signInNewUser(AppUserRepository userRepository) {
		AppUser user = new AppUser();
		user.setEmail(UUID.randomUUID() + "@test.local");
		user.setDisplayName("Test User");
		AppUser saved = userRepository.save(user);
		AppUserPrincipal principal = new AppUserPrincipal(
				saved.getId(), saved.getEmail(), saved.getDisplayName(), "unused", UserRole.USER);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
		return saved.getId();
	}
}
