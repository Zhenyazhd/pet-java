package com.jobsearch.joblist.config;

import com.jobsearch.joblist.source.SourceName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Every setting of the service; defaults live in application.yml. */
@Validated
@ConfigurationProperties("job-list")
public record JobListProperties(
		@NotEmpty List<@Valid Client> clients,
		@Valid Apify apify,
		@Valid Llm llm,
		Map<SourceName, SourceSettings> sources,
		@Positive int maxItemsPerSource,
		@Positive int firstRunLookbackDays,
		@Positive int maxRankedPerRun,
		@Positive int maxDescriptionChars,
		double mapperDropThreshold,
		@Positive int retentionDays,
		@Positive int runRetentionDays,
		@Positive int rankWindowDays,
		@Positive int maxProfilesPerOwner,
		@Positive int maxProfilesPerClient,
		@Positive int maxRunsPerOwnerPerDay,
		@Positive int maxRunsPerClientPerDay
) {

	public record Client(@NotBlank String name, @NotBlank @Size(min = 16) String token) {
	}

	public record Apify(
			@NotBlank String token,
			@NotBlank String baseUrl,
			Duration runTimeout,
			Duration pollInterval,
			BigDecimal maxCostPerSourceUsd,
			BigDecimal monthlyBudgetUsd
	) {
	}

	public record Llm(
			@NotBlank String apiKey,
			@NotBlank String baseUrl,
			@NotBlank String model,
			@Positive int batchSize,
			Duration timeout,
			@NotNull @Positive BigDecimal maxCostPerCallUsd,
			@NotNull @Positive BigDecimal monthlyBudgetUsd
	) {
	}

	public record SourceSettings(boolean enabled, int minIntervalHours) {
	}

	public SourceSettings source(SourceName name) {
		return sources.get(name);
	}
}
