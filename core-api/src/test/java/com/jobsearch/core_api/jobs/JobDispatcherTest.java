package com.jobsearch.core_api.jobs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.jobsearch.core_api.TestSupport;
import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.compile.CompileService;
import com.jobsearch.core_api.compile.LatexWorkerClient;
import com.jobsearch.core_api.compile.ResumePdfCacheRepository;
import com.jobsearch.core_api.compile.WorkerResult;
import com.jobsearch.core_api.profile.AppUserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Queue mechanics and the RESUME_PDF handler, with a mocked latex-worker. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class JobDispatcherTest {

	private static final String RESUME_PDF = JobType.RESUME_PDF.name();

	@Autowired
	private JobDispatcher dispatcher;
	@Autowired
	private CompileService compileService;
	@Autowired
	private JobRepository jobRepository;
	@Autowired
	private ResumePdfCacheRepository cacheRepository;
	@Autowired
	private AppUserRepository userRepository;
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
	void compiledPdfIsCachedAndServed() {
		byte[] pdf = { 37, 80, 68, 70 };
		when(workerClient.compile(anyString())).thenReturn(new WorkerResult.Pdf(pdf));
		UUID jobId = UUID.fromString(compileService.enqueue().id());

		dispatcher.process(jobRepository.claimNext(RESUME_PDF, 60).orElseThrow());

		assertEquals(JobStatus.DONE, jobRepository.findById(jobId).orElseThrow().getStatus());
		assertArrayEquals(pdf, compileService.pdf(jobId));
	}

	@Test
	void compileErrorFailsWithTheLatexLog() {
		when(workerClient.compile(anyString()))
				.thenReturn(new WorkerResult.Rejected("compile_error", "Undefined control sequence"));
		UUID jobId = UUID.fromString(compileService.enqueue().id());

		dispatcher.process(jobRepository.claimNext(RESUME_PDF, 60).orElseThrow());

		Job job = jobRepository.findById(jobId).orElseThrow();
		assertEquals(JobStatus.FAILED, job.getStatus());
		assertEquals("compile_error", job.getErrorCode());
		assertEquals("Undefined control sequence", job.getErrorMessage());
	}

	@Test
	void busyRequeuesWithoutUsingAnAttempt() {
		when(workerClient.compile(anyString())).thenReturn(new WorkerResult.Busy(1));
		UUID jobId = queue("hash");

		dispatcher.process(jobRepository.claimNext(RESUME_PDF, 60).orElseThrow());

		Job job = jobRepository.findById(jobId).orElseThrow();
		assertEquals(JobStatus.QUEUED, job.getStatus());
		assertEquals(0, job.getAttempts());
	}

	@Test
	void retryIsRequeuedUntilMaxAttemptsThenFails() {
		when(workerClient.compile(anyString())).thenReturn(new WorkerResult.Unavailable("connection refused"));
		UUID jobId = queue("hash");

		dispatcher.process(jobRepository.claimNext(RESUME_PDF, 60).orElseThrow());
		assertEquals(JobStatus.QUEUED, jobRepository.findById(jobId).orElseThrow().getStatus());
		dispatcher.process(jobRepository.claimNext(RESUME_PDF, 60).orElseThrow());
		dispatcher.process(jobRepository.claimNext(RESUME_PDF, 60).orElseThrow());

		Job job = jobRepository.findById(jobId).orElseThrow();
		assertEquals(JobStatus.FAILED, job.getStatus());
		assertEquals("unavailable", job.getErrorCode());
		assertEquals(3, job.getAttempts());
	}

	@Test
	void outcomeArrivingAfterLeaseWasTakenBackIsDiscarded() {
		when(workerClient.compile(anyString())).thenReturn(new WorkerResult.Pdf(new byte[] { 1 }));
		UUID jobId = queue("hash");
		Job claimed = jobRepository.claimNext(RESUME_PDF, 60).orElseThrow();
		// Simulate the lease being taken back while the handler was still running.
		jobRepository.requeue(jobId, claimed.getAttempts());

		dispatcher.process(claimed);

		assertEquals(JobStatus.QUEUED, jobRepository.findById(jobId).orElseThrow().getStatus());
	}

	@Test
	void claimsOnlyTheRequestedType() {
		jobRepository.save(Job.queued(JobType.ATS_MATCH, userId, "hash", "{}"));

		assertTrue(jobRepository.claimNext(RESUME_PDF, 60).isEmpty());
	}

	@Test
	void parallelClaimsNeverShareAJob() {
		for (int i = 0; i < 20; i++) {
			queue("hash-" + i);
		}

		CompletableFuture<List<UUID>> first = CompletableFuture.supplyAsync(this::claimAll);
		CompletableFuture<List<UUID>> second = CompletableFuture.supplyAsync(this::claimAll);
		List<UUID> claimed = new ArrayList<>(first.join());
		claimed.addAll(second.join());

		assertEquals(20, claimed.size());
		assertEquals(20, new HashSet<>(claimed).size());
	}

	private UUID queue(String dedupeKey) {
		return jobRepository.save(Job.queued(JobType.RESUME_PDF, userId, dedupeKey, "source")).getId();
	}

	private List<UUID> claimAll() {
		List<UUID> ids = new ArrayList<>();
		Optional<Job> job;
		while ((job = jobRepository.claimNext(RESUME_PDF, 60)).isPresent()) {
			ids.add(job.get().getId());
		}
		return ids;
	}
}
