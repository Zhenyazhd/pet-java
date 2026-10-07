package com.jobsearch.core_api.compile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import com.jobsearch.core_api.TestSupport;
import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.jobs.JobDtos.JobResponse;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CompileServiceTest {

	@Autowired
	private CompileService service;
	@Autowired
	private JobRepository jobRepository;
	@Autowired
	private ResumePdfCacheRepository cacheRepository;
	@Autowired
	private AppUserRepository userRepository;
	@MockitoBean
	private LatexWorkerClient workerClient;

	@BeforeEach
	void setUp() {
		TestSupport.clearJobsAndPdfCache(jobRepository, cacheRepository);
		TestSupport.signInNewUser(userRepository);
	}

	@AfterEach
	void signOut() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void cacheHitIsDoneWithoutCallingTheWorker() {
		JobResponse queued = service.enqueue();
		String hash = jobRepository.findById(UUID.fromString(queued.id())).orElseThrow().getDedupeKey();
		cacheRepository.upsert(hash, new byte[] { 1, 2, 3 });

		JobResponse cached = service.enqueue();

		assertEquals("DONE", cached.status());
		assertArrayEquals(new byte[] { 1, 2, 3 }, service.pdf(UUID.fromString(cached.id())));
		verifyNoInteractions(workerClient);
	}

	@Test
	void pdfOfUnfinishedJobIsConflict() {
		UUID jobId = UUID.fromString(service.enqueue().id());

		assertThrows(ConflictException.class, () -> service.pdf(jobId));
	}
}
