package com.jobsearch.core_api.compile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.profile.AppUserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CompileDispatcherTest {

	@Autowired
	private CompileDispatcher dispatcher;
	@Autowired
	private CompileJobService service;
	@Autowired
	private CompileJobRepository jobRepository;
	@Autowired
	private ResumePdfCacheRepository cacheRepository;
	@Autowired
	private AppUserRepository userRepository;
	@MockitoBean
	private LatexWorkerClient workerClient;

	private long userId;

	@BeforeEach
	void setUp() {
		CompileTestSupport.clearQueueAndCache(jobRepository, cacheRepository);
		userId = CompileTestSupport.signInNewUser(userRepository);
	}

	@AfterEach
	void signOut() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void storesPdfAndFinishesJob() {
		byte[] pdf = { 37, 80, 68, 70 };
		when(workerClient.compile(anyString())).thenReturn(new WorkerResult.Pdf(pdf));
		UUID jobId = enqueueAndProcess();

		CompileJob job = jobRepository.findById(jobId).orElseThrow();
		assertEquals(CompileJobStatus.DONE, job.getStatus());
		assertNull(job.getSource());
		assertArrayEquals(pdf, service.pdf(jobId));
	}

	@Test
	void compileErrorFailsWithTheLatexLog() {
		when(workerClient.compile(anyString()))
				.thenReturn(new WorkerResult.Rejected("compile_error", "Undefined control sequence"));
		UUID jobId = enqueueAndProcess();

		CompileJob job = jobRepository.findById(jobId).orElseThrow();
		assertEquals(CompileJobStatus.FAILED, job.getStatus());
		assertEquals("compile_error", job.getErrorCode());
		assertEquals("Undefined control sequence", job.getErrorMessage());
	}

	@Test
	void busyWorkerRequeuesWithoutUsingAnAttempt() {
		when(workerClient.compile(anyString())).thenReturn(new WorkerResult.Busy(1));
		UUID jobId = enqueueAndProcess();

		CompileJob job = jobRepository.findById(jobId).orElseThrow();
		assertEquals(CompileJobStatus.QUEUED, job.getStatus());
		assertEquals(0, job.getAttempts());
	}

	@Test
	void unavailableWorkerIsRetriedThenFails() {
		when(workerClient.compile(anyString())).thenReturn(new WorkerResult.Unavailable("connection refused"));
		UUID jobId = UUID.fromString(service.enqueue().id());

		dispatcher.process(jobRepository.claimNext(60).orElseThrow());
		assertEquals(CompileJobStatus.QUEUED, jobRepository.findById(jobId).orElseThrow().getStatus());
		dispatcher.process(jobRepository.claimNext(60).orElseThrow());
		dispatcher.process(jobRepository.claimNext(60).orElseThrow());

		CompileJob job = jobRepository.findById(jobId).orElseThrow();
		assertEquals(CompileJobStatus.FAILED, job.getStatus());
		assertEquals("worker_unavailable", job.getErrorCode());
		assertEquals(3, job.getAttempts());
	}

	@Test
	void resultArrivingAfterLeaseWasTakenBackIsDiscarded() {
		when(workerClient.compile(anyString())).thenReturn(new WorkerResult.Pdf(new byte[] { 1 }));
		UUID jobId = UUID.fromString(service.enqueue().id());
		CompileJob claimed = jobRepository.claimNext(60).orElseThrow();
		// Simulate the lease being taken back while the worker was still compiling.
		jobRepository.requeue(jobId, claimed.getAttempts());

		dispatcher.process(claimed);

		assertEquals(CompileJobStatus.QUEUED, jobRepository.findById(jobId).orElseThrow().getStatus());
	}

	@Test
	void parallelClaimsNeverShareAJob() {
		for (int i = 0; i < 20; i++) {
			jobRepository.save(CompileJob.queued(userId, "hash-" + i, "source"));
		}

		CompletableFuture<List<UUID>> first = CompletableFuture.supplyAsync(this::claimAll);
		CompletableFuture<List<UUID>> second = CompletableFuture.supplyAsync(this::claimAll);
		List<UUID> claimed = new ArrayList<>(first.join());
		claimed.addAll(second.join());

		Set<UUID> unique = new HashSet<>(claimed);
		assertEquals(20, claimed.size());
		assertEquals(20, unique.size());
		assertTrue(jobRepository.claimNext(60).isEmpty());
	}

	private UUID enqueueAndProcess() {
		UUID jobId = UUID.fromString(service.enqueue().id());
		dispatcher.process(jobRepository.claimNext(60).orElseThrow());
		return jobId;
	}

	private List<UUID> claimAll() {
		List<UUID> ids = new ArrayList<>();
		Optional<CompileJob> job;
		while ((job = jobRepository.claimNext(60)).isPresent()) {
			ids.add(job.get().getId());
		}
		return ids;
	}
}
