package com.jobsearch.core_api.jobsearch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jobsearch.core_api.config.AppProperties;
import org.junit.jupiter.api.Test;

class JobListConfigTest {

	private static AppProperties props(String token, String url) {
		AppProperties p = new AppProperties();
		p.getJobList().setToken(token);
		p.getJobList().setBaseUrl(url);
		return p;
	}

	@Test
	void aConfiguredFeatureNeedsAnAbsoluteHttpUrl() {
		JobListConfig config = new JobListConfig();
		for (String bad : new String[] { "", "  ", "job-list:8100", "ftp://x", "/v1", "http://" }) {
			assertThrows(IllegalStateException.class, () -> config.jobListRestClient(props("t", bad)), bad);
		}
		assertDoesNotThrow(() -> config.jobListRestClient(props("t", "http://job-list:8100/")));
	}

	@Test
	void aDisabledFeatureIgnoresTheUrl() {
		assertDoesNotThrow(() -> new JobListConfig().jobListRestClient(props("", "")));
	}
}
