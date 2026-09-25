package com.jobsearch.core_api.profile;

import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.config.AppProperties;
import com.jobsearch.core_api.profile.ProfileDtos.CareerPathRequest;
import com.jobsearch.core_api.profile.ProfileDtos.CareerPathResponse;
import com.jobsearch.core_api.profile.ProfileDtos.ProfileRequest;
import com.jobsearch.core_api.profile.ProfileDtos.ProfileResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profile + career-path storage for the temporary single-user mode. */
@Service
@Transactional
public class ProfileService {

	private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

	private final AppProperties appProperties;
	private final AppUserRepository appUserRepository;

	public ProfileService(AppProperties appProperties, AppUserRepository appUserRepository) {
		this.appProperties = appProperties;
		this.appUserRepository = appUserRepository;
	}

	@Transactional(readOnly = true)
	public ProfileResponse getProfile() {
		return toResponse(currentUser());
	}

	public ProfileResponse saveProfile(ProfileRequest request) {
		AppUser user = currentUser();
		user.setDisplayName(request.displayName().strip());
		user.setEmail(request.email().strip());
		user.setCareerPath(request.careerPath() == null ? "" : request.careerPath());
		ProfileResponse saved = toResponse(appUserRepository.save(user));
		log.info("Saved profile for userId={} careerPathChars={}", user.getId(), saved.careerPath().length());
		return saved;
	}

	@Transactional(readOnly = true)
	public CareerPathResponse getCareerPath() {
		return new CareerPathResponse(currentUser().getCareerPath());
	}

	public CareerPathResponse saveCareerPath(CareerPathRequest request) {
		AppUser user = currentUser();
		user.setCareerPath(request.careerPath() == null ? "" : request.careerPath());
		return new CareerPathResponse(appUserRepository.save(user).getCareerPath());
	}

	private ProfileResponse toResponse(AppUser user) {
		return new ProfileResponse(user.getDisplayName(), user.getEmail(), user.getCareerPath());
	}

	private AppUser currentUser() {
		long userId = appProperties.getCurrentUserId();
		return appUserRepository.findById(userId)
				.orElseThrow(() -> new NotFoundException("User not found: " + userId));
	}
}
