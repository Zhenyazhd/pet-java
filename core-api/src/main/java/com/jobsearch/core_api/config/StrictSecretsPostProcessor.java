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
		SecretStrength.require("LATEX_WORKER_TOKEN", environment.getProperty("app.latex-worker.token"));
	}
}
