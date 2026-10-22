package com.jobsearch.core_api.auth;

import com.jobsearch.core_api.common.UnauthorizedException;
import com.jobsearch.core_api.profile.AppUser;
import com.jobsearch.core_api.profile.AppUserRepository;
import org.springframework.stereotype.Component;

/** Resolves the authenticated app_user for service-layer ownership checks. */
@Component
public class CurrentUserService {

	private final AppUserRepository appUserRepository;

	public CurrentUserService(AppUserRepository appUserRepository) {
		this.appUserRepository = appUserRepository;
	}

	public long requireUserId() {
		AppUserPrincipal principal = AppUserPrincipal.current();
		if (principal == null) {
			throw new UnauthorizedException("Not authenticated");
		}
		return principal.getId();
	}

	public AppUser requireUser() {
		long userId = requireUserId();
		return appUserRepository.findById(userId)
				.orElseThrow(() -> new UnauthorizedException("User not found"));
	}
}
