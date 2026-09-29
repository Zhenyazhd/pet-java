package com.jobsearch.core_api.auth;

import com.jobsearch.core_api.config.AppProperties;
import com.jobsearch.core_api.profile.AppUser;
import com.jobsearch.core_api.profile.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstraps seed user password (optional) and keeps id=1 as ADMIN for invite management.
 */
@Component
public class AuthBootstrap {

	private static final Logger log = LoggerFactory.getLogger(AuthBootstrap.class);

	private final AppProperties appProperties;
	private final AppUserRepository appUserRepository;
	private final PasswordEncoder passwordEncoder;

	public AuthBootstrap(
			AppProperties appProperties,
			AppUserRepository appUserRepository,
			PasswordEncoder passwordEncoder
	) {
		this.appProperties = appProperties;
		this.appUserRepository = appUserRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void ensureSeedAdmin() {
		AppUser user = appUserRepository.findById(1L).orElse(null);
		if (user == null) {
			log.warn("Seed user id=1 is missing");
			return;
		}

		boolean changed = false;
		if (!UserRole.ADMIN.name().equalsIgnoreCase(user.getRole())) {
			user.setRole(UserRole.ADMIN.name());
			changed = true;
			log.info("Promoted seed user id=1 to ADMIN");
		}

		String bootstrapPassword = appProperties.getBootstrapPassword();
		if (bootstrapPassword != null
				&& !bootstrapPassword.isBlank()
				&& (user.getPasswordHash() == null || user.getPasswordHash().isBlank())) {
			user.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
			changed = true;
			log.info("Set bootstrap password for seed user id=1 email={}", user.getEmail());
		}

		if (changed) {
			appUserRepository.save(user);
		}
	}
}
