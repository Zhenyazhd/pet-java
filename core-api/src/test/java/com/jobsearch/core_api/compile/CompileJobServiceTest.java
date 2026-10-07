package com.jobsearch.core_api.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.compile.CompileDtos.CompileJobResponse;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CompileJobServiceTest {

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
	void queuesNewSource() {
		CompileJobResponse job = service.enqueue();

		assertEquals("QUEUED", job.status());
	}

	@Test
	void doubleEnqueueReturnsTheSameActiveJob() {
		CompileJobResponse first = service.enqueue();
		CompileJobResponse second = service.enqueue();

		assertEquals(first.id(), second.id());
	}

	@Test
	void cacheHitIsDoneWithoutCallingTheWorker() {
		CompileJobResponse queued = service.enqueue();
		String hash = jobRepository.findById(UUID.fromString(queued.id())).orElseThrow().getSourceHash();
		cacheRepository.upsert(hash, new byte[] { 1, 2, 3 });

		CompileJobResponse cached = service.enqueue();

		assertEquals("DONE", cached.status());
		assertEquals(3, service.pdf(UUID.fromString(cached.id())).length);
		verifyNoInteractions(workerClient);
	}

	@Test
	void pdfOfUnfinishedJobIsConflict() {
		UUID jobId = UUID.fromString(service.enqueue().id());

		assertThrows(ConflictException.class, () -> service.pdf(jobId));
	}

	@Test
	void anotherUsersJobIsNotFound() {
		UUID jobId = UUID.fromString(service.enqueue().id());

		CompileTestSupport.signInNewUser(userRepository);

		assertThrows(NotFoundException.class, () -> service.get(jobId));
		assertThrows(NotFoundException.class, () -> service.pdf(jobId));
	}
}
