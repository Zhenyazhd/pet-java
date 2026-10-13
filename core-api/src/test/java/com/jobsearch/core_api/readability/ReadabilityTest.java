package com.jobsearch.core_api.readability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobsearch.core_api.TestSupport;
import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.Hashes;
import com.jobsearch.core_api.compile.ResumePdfCacheRepository;
import com.jobsearch.core_api.jobs.Job;
import com.jobsearch.core_api.jobs.JobDtos.JobResponse;
import com.jobsearch.core_api.jobs.JobOutcome;
import com.jobsearch.core_api.jobs.JobRepository;
import com.jobsearch.core_api.jobs.JobService;
import com.jobsearch.core_api.jobs.JobType;
import com.jobsearch.core_api.profile.AppUserRepository;
import com.jobsearch.core_api.resume.ResumeService;
import java.time.Instant;
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
import tools.jackson.databind.ObjectMapper;

/** Queueing a readability check of a built PDF and running it through {@link ReadabilityJobHandler}, worker mocked. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ReadabilityTest {

	private static final byte[] PDF = {37, 80, 68, 70, 45};
	private static final String REPORT = "{\"verdict\":\"warnings\",\"score\":80,\"issues\":[{\"code\":\"columns\"}]}";

	@Autowired
	private ReadabilityService service;
	@Autowired
	private ReadabilityJobHandler handler;
	@Autowired
	private JobService jobService;
	@Autowired
	private JobRepository jobRepository;
	@Autowired
	private ResumePdfCacheRepository cacheRepository;
	@Autowired
	private ResumeService resumeService;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private ObjectMapper objectMapper;
	@MockitoBean
	private AtsWorkerClient workerClient;

	@BeforeEach
	void setUp() {
		TestSupport.clearJobs(jobRepository);
		TestSupport.signInNewUser(userRepository);
	}

	@AfterEach
	void signOut() {
		SecurityContextHolder.clearContext();
	}

	/** A finished PDF build of the current resume, with the PDF in the cache. */
	private UUID builtPdf() {
		String hash = Hashes.sha256(resumeService.toLatex());
		cacheRepository.upsert(hash, PDF);
		return UUID.fromString(jobService.enqueueDone(JobType.RESUME_PDF, hash).id());
	}

	private Job claim() {
		return jobRepository.claimNext(JobType.PDF_READABILITY.name(), 60).orElseThrow();
	}

	@Test
	void theWorkersReportBecomesTheJobResult() {
		when(workerClient.check(any(), anyString())).thenReturn(new AtsWorkerResult.Report(objectMapper.readTree(REPORT)));
		JobResponse queued = service.enqueue(builtPdf());
		assertEquals("QUEUED", queued.status());

		JobOutcome outcome = handler.run(claim());

		JobOutcome.Succeeded succeeded = assertInstanceOf(JobOutcome.Succeeded.class, outcome);
		assertEquals(80, objectMapper.readTree(succeeded.result()).path("score").asInt());
		// The worker is given the cached PDF and the plain text of the resume.
		verify(workerClient).check(org.mockito.ArgumentMatchers.eq(PDF), contains("Experience"));
	}

	@Test
	void aSecondClickWhileTheCheckIsActiveReusesTheJob() {
		UUID compile = builtPdf();

		assertEquals(service.enqueue(compile).id(), service.enqueue(compile).id());
	}

	@Test
	void aResumeEditedAfterTheBuildIsRefused() {
		UUID stale = UUID.fromString(jobService.enqueueDone(JobType.RESUME_PDF, "0".repeat(64)).id());

		assertThrows(ConflictException.class, () -> service.enqueue(stale));
	}

	@Test
	void aPdfThatIsNotBuiltYetIsRefused() {
		UUID running = UUID.fromString(jobService.enqueue(JobType.RESUME_PDF, "1".repeat(64), "source").id());

		assertThrows(ConflictException.class, () -> service.enqueue(running));
	}

	@Test
	void aPdfDeletedFromTheCacheFailsTheJob() {
		UUID compile = builtPdf();
		service.enqueue(compile);
		cacheRepository.deleteUnusedBefore(Instant.now().plusSeconds(60), 100);

		assertEquals(new JobOutcome.Failed("pdf_expired", "The PDF has expired. Build the PDF again."), handler.run(claim()));
	}

	@Test
	void workerAnswersMapToOutcomes() {
		service.enqueue(builtPdf());
		Job job = claim();

		when(workerClient.check(any(), anyString())).thenReturn(new AtsWorkerResult.Rejected("encrypted", "Password protected"));
		assertEquals(new JobOutcome.Failed("encrypted", "Password protected"), handler.run(job));

		// An internal rejection (bad token, bad request) is our bug, not something to show the user.
		when(workerClient.check(any(), anyString())).thenReturn(new AtsWorkerResult.Rejected("unauthorized", "Missing token"));
		assertEquals(new JobOutcome.Failed("unauthorized", "The PDF could not be checked. Please try again later."),
				handler.run(job));

		when(workerClient.check(any(), anyString())).thenReturn(new AtsWorkerResult.Busy(3));
		assertInstanceOf(JobOutcome.Busy.class, handler.run(job));

		when(workerClient.check(any(), anyString())).thenReturn(new AtsWorkerResult.Unavailable("refused"));
		assertInstanceOf(JobOutcome.Retry.class, handler.run(job));
	}
}
