package com.jobsearch.core_api.profile;

import com.jobsearch.core_api.profile.ProfileDtos.CareerPathRequest;
import com.jobsearch.core_api.profile.ProfileDtos.CareerPathResponse;
import com.jobsearch.core_api.profile.ProfileDtos.ProfileRequest;
import com.jobsearch.core_api.profile.ProfileDtos.ProfileResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** User profile (name, email, career path) for the current temporary user. */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

	private final ProfileService profileService;

	public ProfileController(ProfileService profileService) {
		this.profileService = profileService;
	}

	@GetMapping
	public ProfileResponse getProfile() {
		return profileService.getProfile();
	}

	@PutMapping
	public ProfileResponse saveProfile(@Valid @RequestBody ProfileRequest request) {
		return profileService.saveProfile(request);
	}

	@GetMapping("/career-path")
	public CareerPathResponse getCareerPath() {
		return profileService.getCareerPath();
	}

	@PutMapping("/career-path")
	public CareerPathResponse saveCareerPath(@Valid @RequestBody CareerPathRequest request) {
		return profileService.saveCareerPath(request);
	}
}
