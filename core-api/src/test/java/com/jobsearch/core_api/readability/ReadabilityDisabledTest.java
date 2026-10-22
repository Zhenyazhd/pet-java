package com.jobsearch.core_api.readability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobsearch.core_api.TestSupport;
import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.common.ServiceUnavailableException;
import com.jobsearch.core_api.jobs.JobRepository;
import com.jobsearch.core_api.profile.AppUserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/** With the check switched off (the default) it is refused cleanly and nothing is queued. */
@SpringBootTest(properties = "app.ats-worker.enabled=false")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ReadabilityDisabledTest {

	@Autowired
	private ReadabilityService service;
	@Autowired
	private ReadabilityJobHandler handler;
	@Autowired
	private JobRepository jobRepository;
	@Autowired
	private AppUserRepository userRepository;

	@BeforeEach
	void signIn() {
		TestSupport.clearJobs(jobRepository);
		TestSupport.signInNewUser(userRepository);
	}

	@AfterEach
	void signOut() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void enqueueIsRefusedAndTheHandlerIsDisabled() {
		assertThrows(ServiceUnavailableException.class, () -> service.enqueue(UUID.randomUUID()));

		assertTrue(handler.disabled());
		assertEquals(0, jobRepository.count());
	}
}
