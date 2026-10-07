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

	/** Creates a user and makes it the authenticated user of the current thread; returns its id. */
	public static long signInNewUser(AppUserRepository userRepository) {
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

	/**
	 * Every new user has the same default resume and so the same LaTeX hash: a PDF cached by one
	 * test would turn the next test's compile into a cache hit. Jobs left by one test would be
	 * claimed by the next.
	 */
	public static void clearJobsAndPdfCache(JobRepository jobRepository, ResumePdfCacheRepository cacheRepository) {
		jobRepository.deleteAll();
		cacheRepository.deleteAll();
	}
}
