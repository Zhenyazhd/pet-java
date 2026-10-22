package com.jobsearch.core_api.jobsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.common.FixedWindowRateLimiter;
import com.jobsearch.core_api.auth.AppUserRepository;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Starting a run costs real money, so it shares the per-user expensive-operations limit. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "app.expensive-ops-rate-limit.per-user-per-minute=2")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class JobSearchRateLimitTest {

	private static final JobListStub STUB = start();

	@Value("${local.server.port}")
	private int port;
	@Autowired
	private AppUserRepository users;
	@Autowired
	private PasswordEncoder encoder;
	@Autowired
	private FixedWindowRateLimiter rateLimiter;

	private static JobListStub start() {
		try {
			return new JobListStub();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void jobList(DynamicPropertyRegistry registry) {
		registry.add("app.job-list.base-url", STUB::baseUrl);
		registry.add("app.job-list.token", () -> JobListStub.TOKEN);
	}

	@AfterAll
	static void stop() {
		STUB.stop();
	}

	@Test
	void thirdRunInAMinuteIsLimitedButOtherCallsAreNot() throws Exception {
		rateLimiter.clear();
		Browser user = new Browser(port, users, encoder, true);
		user.send("PUT", "/api/job-search/profile", "{\"expectations\":\"x\",\"roles\":[\"r\"]}");

		assertEquals(202, user.send("POST", "/api/job-search/runs", null).statusCode());
		assertEquals(202, user.send("POST", "/api/job-search/runs", null).statusCode());
		HttpResponse<String> limited = user.send("POST", "/api/job-search/runs", null);

		assertEquals(429, limited.statusCode());
		assertNotNull(limited.headers().firstValue("Retry-After").orElse(null));
		assertNotEquals(429, user.send("GET", "/api/job-search", null).statusCode());
		assertEquals(2, STUB.requests.stream().filter(r -> r.startsWith("POST /v1/profiles/") && r.endsWith("/runs")).count());
	}

	@Test
	void rankRunsShareTheRunBucket() throws Exception {
		rateLimiter.clear();
		Browser user = new Browser(port, users, encoder, true);
		user.send("PUT", "/api/job-search/profile", "{\"expectations\":\"x\",\"roles\":[\"r\"]}");
		STUB.unranked.put(STUB.profiles.getLast().id(), 5);

		assertEquals(202, user.send("POST", "/api/job-search/runs", null).statusCode());
		assertEquals(202, user.send("POST", "/api/job-search/runs/rank", null).statusCode());
		HttpResponse<String> limited = user.send("POST", "/api/job-search/runs/rank", null);

		assertEquals(429, limited.statusCode());
		assertNotNull(limited.headers().firstValue("Retry-After").orElse(null));
		assertEquals(429, user.send("POST", "/api/job-search/runs", null).statusCode());
	}

	@Test
	void seenHasItsOwnMoreGenerousBucket() throws Exception {
		rateLimiter.clear();
		Browser user = new Browser(port, users, encoder, true);

		for (int i = 0; i < 12; i++) {
			assertNotEquals(429, user.send("POST", "/api/job-search/matches/" + i + "/seen", null).statusCode());
		}
		assertEquals(429, user.send("POST", "/api/job-search/matches/13/seen", null).statusCode());
		assertEquals(409, user.send("POST", "/api/job-search/runs", null).statusCode());
	}
}
