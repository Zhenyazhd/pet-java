package com.jobsearch.core_api.profile;

import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.Emails;
import com.jobsearch.core_api.common.UniqueConstraint;
import com.jobsearch.core_api.profile.ProfileDtos.CareerPathRequest;
import com.jobsearch.core_api.profile.ProfileDtos.CareerPathResponse;
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
		String email = Emails.normalize(request.email());
		if (!email.equalsIgnoreCase(user.getEmail())
				&& appUserRepository.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) {
			throw new ConflictException("Email already registered");
		}

		user.setDisplayName(request.displayName().strip());
		user.setEmail(email);
		user.setCareerPath(request.careerPath() == null ? "" : request.careerPath());
		ProfileResponse saved = UniqueConstraint.onConflict(
				"Email already registered",
				() -> toResponse(appUserRepository.saveAndFlush(user)),
				UniqueConstraint.APP_USER_EMAIL_LOWER
		);
		log.info("Saved profile for userId={} careerPathChars={}", user.getId(), saved.careerPath().length());
		return saved;
	}

	@Transactional(readOnly = true)
	public CareerPathResponse getCareerPath() {
		return new CareerPathResponse(currentUserService.requireUser().getCareerPath());
	}

	public CareerPathResponse saveCareerPath(CareerPathRequest request) {
		AppUser user = currentUserService.requireUser();
		user.setCareerPath(request.careerPath() == null ? "" : request.careerPath());
		return new CareerPathResponse(appUserRepository.save(user).getCareerPath());
	}

	private ProfileResponse toResponse(AppUser user) {
		return new ProfileResponse(user.getDisplayName(), user.getEmail(), user.getCareerPath());
	}
}
