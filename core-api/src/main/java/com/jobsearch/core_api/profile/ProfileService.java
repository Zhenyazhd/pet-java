package com.jobsearch.core_api.profile;

import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.common.Emails;
import com.jobsearch.core_api.common.UniqueConstraint;
import com.jobsearch.core_api.profile.ProfileDtos.ProfileRequest;
import com.jobsearch.core_api.profile.ProfileDtos.ProfileResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profile + career-path storage for the authenticated user. */
@Service
@Transactional
public class ProfileService {

	private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

	private final CurrentUserService currentUserService;
	private final AppUserRepository appUserRepository;

	public ProfileService(CurrentUserService currentUserService, AppUserRepository appUserRepository) {
		this.currentUserService = currentUserService;
		this.appUserRepository = appUserRepository;
	}

	@Transactional(readOnly = true)
	public ProfileResponse getProfile() {
		return toResponse(currentUserService.requireUser());
	}

	public ProfileResponse saveProfile(ProfileRequest request) {
		AppUser user = currentUserService.requireUser();
		user.setDisplayName(request.displayName().strip());
		user.setEmail(Emails.normalize(request.email()));
		user.setCareerPath(request.careerPath());
		ProfileResponse saved = UniqueConstraint.onConflict(
				"Email already registered",
				() -> toResponse(appUserRepository.saveAndFlush(user)),
				UniqueConstraint.APP_USER_EMAIL_LOWER
		);
		log.info("Saved profile for userId={} careerPathChars={}", user.getId(), saved.careerPath().length());
		return saved;
	}

	private ProfileResponse toResponse(AppUser user) {
		return new ProfileResponse(user.getDisplayName(), user.getEmail(), user.getCareerPath());
	}
}
