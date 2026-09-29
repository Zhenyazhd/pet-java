package com.jobsearch.core_api.auth;

import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.Emails;
import com.jobsearch.core_api.common.UniqueConstraint;
import com.jobsearch.core_api.config.AppProperties;
import com.jobsearch.core_api.profile.AppUser;
import com.jobsearch.core_api.profile.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ensures a bootstrap ADMIN always exists (by configured email), so invite codes can be created
 * even on a fresh database without the legacy id=1 seed user.
 *
 * <p>Uses {@link ApplicationRunner} so a missing {@code APP_ADMIN_EMAIL} in {@code prod} aborts startup.
 */
@Component
public class AuthBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AuthBootstrap.class);

	private final AppProperties appProperties;
	private final AppUserRepository appUserRepository;
	private final PasswordEncoder passwordEncoder;
	private final Environment environment;
	private final TransactionTemplate requiresNewTx;

	public AuthBootstrap(
			AppProperties appProperties,
			AppUserRepository appUserRepository,
			PasswordEncoder passwordEncoder,
			Environment environment,
			PlatformTransactionManager transactionManager
	) {
		this.appProperties = appProperties;
		this.appUserRepository = appUserRepository;
		this.passwordEncoder = passwordEncoder;
		this.environment = environment;
		this.requiresNewTx = new TransactionTemplate(transactionManager);
		this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		String adminEmail = Emails.normalize(appProperties.getAdminEmail());
		if (adminEmail.isBlank()) {
			if (environment.matchesProfiles("prod", "production")) {
				throw new IllegalStateException(
						"APP_ADMIN_EMAIL / app.admin-email is required in production — refusing to start without a bootstrap ADMIN"
				);
			}
			log.error(
					"app.admin-email is empty — set APP_ADMIN_EMAIL; no ADMIN can be bootstrapped and registration will stay locked"
			);
			return;
		}

		AppUser user = appUserRepository.findByEmailIgnoreCase(adminEmail).orElse(null);
		if (user == null) {
			user = createBootstrapAdmin(adminEmail);
		}

		boolean changed = false;
		if (!UserRole.ADMIN.name().equalsIgnoreCase(user.getRole())) {
			user.setRole(UserRole.ADMIN.name());
			changed = true;
			log.info("Promoted user id={} email={} to ADMIN", user.getId(), adminEmail);
		}

		String bootstrapPassword = appProperties.getBootstrapPassword();
		if (bootstrapPassword != null
				&& !bootstrapPassword.isBlank()
				&& (user.getPasswordHash() == null || user.getPasswordHash().isBlank())) {
			user.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
			changed = true;
			log.info("Set bootstrap password for ADMIN email={}", adminEmail);
		}

		if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
			log.warn(
					"ADMIN email={} has no password — set APP_BOOTSTRAP_PASSWORD or update the account before login",
					adminEmail
			);
		}

		if (changed) {
			appUserRepository.save(user);
		}
	}

	/**
	 * Insert runs in {@code REQUIRES_NEW} so a unique-constraint race rolls back only the nested
	 * transaction; the outer {@link #run} TX stays healthy for the reload query.
	 * After a race, retry find briefly — the winning insert may not be visible on the first read
	 * (isolation / commit timing under rolling deploy); never rethrow {@link ConflictException}
	 * from {@link ApplicationRunner} or the instance fails to start.
	 */
	private AppUser createBootstrapAdmin(String adminEmail) {
		log.info("Creating bootstrap ADMIN email={}", adminEmail);
		try {
			return UniqueConstraint.onConflict(
					"Bootstrap admin already exists",
					() -> requiresNewTx.execute(status -> {
						AppUser created = new AppUser();
						created.setEmail(adminEmail);
						created.setDisplayName(Emails.localPart(adminEmail));
						created.setCareerPath("");
						created.setResumeJson("");
						created.setRole(UserRole.ADMIN.name());
						return appUserRepository.saveAndFlush(created);
					}),
					UniqueConstraint.APP_USER_EMAIL_LOWER
			);
		}
		catch (ConflictException ex) {
			log.info("Bootstrap ADMIN create raced for email={}, loading existing row", adminEmail);
			return waitForExistingBootstrapAdmin(adminEmail, ex);
		}
	}

	private AppUser waitForExistingBootstrapAdmin(String adminEmail, ConflictException race) {
		for (int attempt = 1; attempt <= 20; attempt++) {
			AppUser existing = appUserRepository.findByEmailIgnoreCase(adminEmail).orElse(null);
			if (existing != null) {
				return existing;
			}
			try {
				Thread.sleep(50L);
			}
			catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(
						"Interrupted while waiting for bootstrap ADMIN email=" + adminEmail,
						ie
				);
			}
		}
		throw new IllegalStateException(
				"Bootstrap ADMIN unique conflict for email=" + adminEmail
						+ " but row not visible after retries — check DB connectivity / isolation",
				race
		);
	}
}
