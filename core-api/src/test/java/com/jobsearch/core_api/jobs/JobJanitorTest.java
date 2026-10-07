package com.jobsearch.core_api.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.jobsearch.core_api.TestSupport;
import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.compile.LatexWorkerClient;
import com.jobsearch.core_api.compile.ResumePdfCacheRepository;
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
class JobJanitorTest {

	@Autowired
	private JobJanitor janitor;
	@Autowired
	private JobRepository jobRepository;
	@Autowired
	private ResumePdfCacheRepository cacheRepository;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@MockitoBean
	private LatexWorkerClient workerClient;

	private long userId;

	@BeforeEach
	void setUp() {
		TestSupport.clearJobsAndPdfCache(jobRepository, cacheRepository);
		userId = TestSupport.signInNewUser(userRepository);
	}

	@AfterEach
	void signOut() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void requeuesExpiredLeaseUntilMaxAttemptsThenFails() throws InterruptedException {
		UUID jobId = queue();

		for (int attempt = 1; attempt <= 3; attempt++) {
			// A zero-second lease is already expired by the time the janitor looks at it.
			jobRepository.claimNext(JobType.RESUME_PDF.name(), 0).orElseThrow();
			Thread.sleep(10);
			janitor.recoverExpiredLeases();
		}

		Job job = jobRepository.findById(jobId).orElseThrow();
		assertEquals(JobStatus.FAILED, job.getStatus());
		assertEquals("unavailable", job.getErrorCode());
	}

	@Test
	void failsJobsQueuedForTooLong() {
		UUID jobId = queue();
		jdbcTemplate.update("UPDATE background_job SET created_at = NOW() - INTERVAL '1 hour' WHERE id = ?", jobId);

		janitor.failStaleQueuedJobs();

		Job job = jobRepository.findById(jobId).orElseThrow();
		assertEquals(JobStatus.FAILED, job.getStatus());
		assertEquals("queue_timeout", job.getErrorCode());
	}

	@Test
	void deletesOldFinishedJobs() {
		UUID jobId = queue();
		jdbcTemplate.update(
				"UPDATE background_job SET status = 'FAILED', finished_at = NOW() - INTERVAL '8 days' WHERE id = ?", jobId);

		janitor.deleteOldJobs();

		assertFalse(jobRepository.existsById(jobId));
	}

	private UUID queue() {
		return jobRepository.save(Job.queued(JobType.RESUME_PDF, userId, "hash", "source")).getId();
	}
}
