package com.jobsearch.core_api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Runs the background jobs (compile dispatcher and janitor). Tests switch it off with
 * {@code app.scheduling-enabled=false} and call those jobs directly.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
