package com.jobsearch.core_api;

import com.jobsearch.core_api.auth.AppUserPrincipal;
import com.jobsearch.core_api.auth.UserRole;
import com.jobsearch.core_api.compile.ResumePdfCacheRepository;
import com.jobsearch.core_api.jobs.JobRepository;
import com.jobsearch.core_api.profile.AppUser;
import com.jobsearch.core_api.profile.AppUserRepository;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

public final class TestSupport {

	private TestSupport() {
	}

	/** A user who exists but is not signed in; returns its id. */
	public static long newUser(AppUserRepository userRepository) {
		AppUser user = new AppUser();
		user.setEmail(UUID.randomUUID() + "@test.local");
		user.setDisplayName("Test User");
		return userRepository.save(user).getId();
	}

	/** Creates a user and makes it the authenticated user of the current thread; returns its id. */
	public static long signInNewUser(AppUserRepository userRepository) {
		long userId = newUser(userRepository);
		AppUser user = userRepository.findById(userId).orElseThrow();
		AppUserPrincipal principal = new AppUserPrincipal(
				userId, user.getEmail(), user.getDisplayName(), "unused", UserRole.USER);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
		return userId;
	}

	/** Jobs left by one test would be claimed by the next. */
	public static void clearJobs(JobRepository jobRepository) {
		jobRepository.deleteAll();
	}

	/**
	 * Every new user has the same default resume and so the same LaTeX hash: a PDF cached by one
	 * test would turn the next test's compile into a cache hit.
	 */
	public static void clearPdfCache(ResumePdfCacheRepository cacheRepository) {
		cacheRepository.deleteAll();
	}
}
