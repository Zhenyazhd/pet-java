package com.jobsearch.joblist;

import com.jobsearch.joblist.config.JobListProperties;
import com.jobsearch.joblist.source.SourceName;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Settings for tests that build components by hand instead of starting Spring. */
public final class TestProperties {

	private TestProperties() {
	}

	public static JobListProperties defaults() {
		return withRunTimeout(Duration.ofSeconds(5));
	}

	/** Defaults with a chosen limit for how long an Apify run may take. */
	public static JobListProperties withRunTimeout(Duration runTimeout) {
		JobListProperties.SourceSettings daily = new JobListProperties.SourceSettings(true, 24);
		return new JobListProperties(
				List.of(new JobListProperties.Client("core-api", "test-token-core-api")),
				new JobListProperties.Apify("token", "http://localhost", runTimeout, Duration.ofMillis(10),
						new BigDecimal("0.30"), new BigDecimal("5")),
				new JobListProperties.Llm("key", "http://localhost", "test-model", 10, Duration.ofSeconds(5), new BigDecimal("0.01"), new BigDecimal("2")),
				Map.of(SourceName.LINKEDIN, daily, SourceName.HELLOWORK, daily, SourceName.GREENHOUSE, daily, SourceName.WTTJ, daily),
				50, 3, 40, 6000, 0.3, 60, 90, 7, 2, 50, 4, 6);
	}
}
