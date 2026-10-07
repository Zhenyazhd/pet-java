package com.jobsearch.core_api.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.profile.AppUserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CompileJanitorTest {

	@Autowired
	private CompileJanitor janitor;
	@Autowired
	private CompileJobService service;
	@Autowired
	private CompileJobRepository jobRepository;
	@Autowired
	private ResumePdfCacheRepository cacheRepository;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@MockitoBean
	private LatexWorkerClient workerClient;

	@BeforeEach
	void setUp() {
		CompileTestSupport.clearQueueAndCache(jobRepository, cacheRepository);
		CompileTestSupport.signInNewUser(userRepository);
	}

	@AfterEach
	void signOut() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void requeuesExpiredLeaseUntilMaxAttemptsThenFails() throws InterruptedException {
		UUID jobId = UUID.fromString(service.enqueue().id());

		for (int attempt = 1; attempt <= 3; attempt++) {
			// A zero-second lease is already expired by the time the janitor looks at it.
			jobRepository.claimNext(0).orElseThrow();
			Thread.sleep(10);
			janitor.recoverExpiredLeases();
		}

		CompileJob job = jobRepository.findById(jobId).orElseThrow();
		assertEquals(CompileJobStatus.FAILED, job.getStatus());
		assertEquals("worker_unavailable", job.getErrorCode());
	}

	@Test
	void failsJobsQueuedForTooLong() {
		UUID jobId = UUID.fromString(service.enqueue().id());
		jdbcTemplate.update("UPDATE compile_job SET created_at = NOW() - INTERVAL '1 hour' WHERE id = ?", jobId);

		janitor.failStaleQueuedJobs();

		CompileJob job = jobRepository.findById(jobId).orElseThrow();
		assertEquals(CompileJobStatus.FAILED, job.getStatus());
		assertEquals("queue_timeout", job.getErrorCode());
	}

	@Test
	void deletesOldFinishedJobs() {
		UUID jobId = UUID.fromString(service.enqueue().id());
		jdbcTemplate.update(
				"UPDATE compile_job SET status = 'FAILED', created_at = NOW() - INTERVAL '8 days' WHERE id = ?", jobId);

		janitor.deleteOldRows();

		assertFalse(jobRepository.existsById(jobId));
	}
}
