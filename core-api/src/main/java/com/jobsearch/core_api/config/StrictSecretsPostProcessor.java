package com.jobsearch.core_api.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

public class StrictSecretsPostProcessor implements EnvironmentPostProcessor, Ordered {

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		if (!environment.getProperty("app.strict-secrets", Boolean.class, false)) {
			return;
		}
		SecretStrength.require("POSTGRES_PASSWORD", environment.getProperty("spring.datasource.password"));
		SecretStrength.require("PDF_WORKER_TOKEN", environment.getProperty("app.pdf-worker.token"));
		if (!environment.getProperty("app.google.client-id", "").isBlank()) {
			SecretStrength.require("APP_GOOGLE_CLIENT_SECRET", environment.getProperty("app.google.client-secret"));
		}
		// Without them a new account could never be confirmed and nobody could reset a password.
		for (String key : new String[] { "spring.mail.host", "app.mail.from", "app.public-base-url" }) {
			String value = environment.getProperty(key);
			if (value == null || value.isBlank()) {
				throw new IllegalStateException(key + " must be set when app.strict-secrets is on");
			}
		}
	}
}
