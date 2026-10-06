package com.jobsearch.core_api.auth;

import com.jobsearch.core_api.common.Emails;
import com.jobsearch.core_api.config.AppProperties;
import com.jobsearch.core_api.profile.AppUser;
import com.jobsearch.core_api.profile.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures a bootstrap ADMIN always exists (by configured email), so invite codes can be created
 * even on a fresh database.
 *
 * <p>Uses {@link ApplicationRunner} so a missing {@code APP_ADMIN_EMAIL} aborts startup in every
 * environment — registration is invite-only and only an ADMIN can mint invite codes, so silently
 * booting without one would permanently lock the deployment out of registration.
 */
@Component
public class AuthBootstrap implements ApplicationRunner {

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

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		String adminEmail = Emails.normalize(appProperties.getAdminEmail());
		if (adminEmail.isBlank()) {
			throw new IllegalStateException(
					"APP_ADMIN_EMAIL / app.admin-email is required — refusing to start without a bootstrap ADMIN"
			);
		}

		AppUser user = appUserRepository.findByEmailIgnoreCase(adminEmail).orElseGet(() -> {
			log.info("Creating bootstrap ADMIN email={}", adminEmail);
			AppUser created = new AppUser();
			created.setEmail(adminEmail);
			created.setDisplayName(Emails.localPart(adminEmail));
			created.setRole(UserRole.ADMIN);
			return created;
		});

		if (user.getRole() != UserRole.ADMIN) {
			user.setRole(UserRole.ADMIN);
			log.info("Promoted user id={} email={} to ADMIN", user.getId(), adminEmail);
		}

		boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
		String bootstrapPassword = appProperties.getBootstrapPassword();
		if (!hasPassword && bootstrapPassword != null && !bootstrapPassword.isBlank()) {
			user.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
			hasPassword = true;
			log.info("Set bootstrap password for ADMIN email={}", adminEmail);
		}
		if (!hasPassword) {
			log.warn(
					"ADMIN email={} has no password — set APP_BOOTSTRAP_PASSWORD or update the account before login",
					adminEmail
			);
		}

		appUserRepository.save(user);
	}
}
