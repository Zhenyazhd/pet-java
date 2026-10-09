package com.jobsearch.core_api.ats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobsearch.core_api.TestSupport;
import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.ats.AtsDtos.MatchRequest;
import com.jobsearch.core_api.auth.AuthService;
import com.jobsearch.core_api.common.ServiceUnavailableException;
import com.jobsearch.core_api.jobs.JobRepository;
import com.jobsearch.core_api.profile.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/** With the vacancy match switched off (the production default) it is refused cleanly and nothing is queued. */
@SpringBootTest(properties = "app.ats-screener.enabled=false")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AtsDisabledTest {

	@Autowired
	private AtsMatchService service;
	@Autowired
	private AtsMatchHandler handler;
	@Autowired
	private AuthService authService;
	@Autowired
	private JobRepository jobRepository;
	@Autowired
	private AppUserRepository userRepository;

	@BeforeEach
	void signIn() {
		TestSupport.signInNewUser(userRepository);
	}

	@AfterEach
	void signOut() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void aMatchRequestIsRefusedWithAFixedMessageAndNoRetryAfter() {
		long jobsBefore = jobRepository.count();

		ServiceUnavailableException ex = assertThrows(ServiceUnavailableException.class,
				() -> service.enqueue(new MatchRequest("Backend engineer, Java", null)));

		assertEquals("The vacancy match is not available yet.", ex.getMessage());
		assertNull(ex.getRetryAfter());
		assertEquals(jobsBefore, jobRepository.count());
	}

	@Test
	void theWorkerLaneStaysClosed() {
		assertTrue(handler.disabled());
	}

	@Test
	void theSessionTellsThePageTheMatchIsOff() {
		assertFalse(authService.me().features().atsMatch());
	}
}
