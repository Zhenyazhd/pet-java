package com.jobsearch.core_api.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jobsearch.core_api.TestSupport;
import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.compile.LatexWorkerClient;
import com.jobsearch.core_api.compile.ResumePdfCacheRepository;
import com.jobsearch.core_api.jobs.JobDtos.JobResponse;
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
class JobServiceTest {

	@Autowired
	private JobService service;
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
	void doubleEnqueueReturnsTheSameActiveJob() {
		JobResponse first = service.enqueue(JobType.RESUME_PDF, "hash", "source");
		JobResponse second = service.enqueue(JobType.RESUME_PDF, "hash", "source");

		assertEquals("QUEUED", first.status());
		assertEquals(first.id(), second.id());
	}

	@Test
	void anotherUsersJobIsNotFound() {
		UUID jobId = UUID.fromString(service.enqueue(JobType.RESUME_PDF, "hash", "source").id());

		TestSupport.signInNewUser(userRepository);

		assertThrows(NotFoundException.class, () -> service.get(jobId, JobType.RESUME_PDF));
	}

	@Test
	void jobOfAnotherTypeIsNotFound() {
		UUID jobId = UUID.fromString(service.enqueue(JobType.RESUME_PDF, "hash", "source").id());

		assertThrows(NotFoundException.class, () -> service.get(jobId, JobType.ATS_MATCH));
	}
}
