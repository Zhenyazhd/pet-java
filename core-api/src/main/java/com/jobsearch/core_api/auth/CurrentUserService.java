package com.jobsearch.core_api.auth;

import com.jobsearch.core_api.common.UnauthorizedException;
import com.jobsearch.core_api.profile.AppUser;
import com.jobsearch.core_api.profile.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resolves the authenticated app_user for service-layer ownership checks. */
@Component
public class CurrentUserService {

	private final AppUserRepository appUserRepository;

	public CurrentUserService(AppUserRepository appUserRepository) {
		this.appUserRepository = appUserRepository;
	}

	public long requireUserId() {
		return requirePrincipal().getId();
	}

	public AppUser requireUser() {
		long userId = requireUserId();
		return appUserRepository.findById(userId)
				.orElseThrow(() -> new UnauthorizedException("User not found"));
	}

	public AppUserPrincipal requirePrincipal() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new UnauthorizedException("Not authenticated");
		}
		Object principal = authentication.getPrincipal();
		if (!(principal instanceof AppUserPrincipal appUserPrincipal)) {
			throw new UnauthorizedException("Not authenticated");
		}
		return appUserPrincipal;
	}
}
