package com.jobsearch.core_api.ats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.Hashes;
import com.jobsearch.core_api.compile.ResumePdfCacheRepository;
import com.jobsearch.core_api.jobs.JobService;
import com.jobsearch.core_api.resume.ResumeService;
import java.time.Instant;
import java.util.UUID;
import com.jobsearch.core_api.TestSupport;
import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.ats.AtsDtos.MatchRequest;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.jobs.Job;
import com.jobsearch.core_api.jobs.JobOutcome;
import com.jobsearch.core_api.jobs.JobRepository;
import com.jobsearch.core_api.jobs.JobType;
import com.jobsearch.core_api.auth.AppUserRepository;
import com.jobsearch.core_api.vacancy.Vacancy;
import com.jobsearch.core_api.vacancy.VacancyRepository;
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

/** Queueing a match and running it through {@link AtsMatchHandler}, with a mocked ats-worker. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AtsMatchTest {

	private static final String TWO_PLATFORMS = """
			{"results": [
			  {"system": "Workday", "vendor": "Workday", "overallScore": 80, "passesFilter": true,
			   "suggestions": [{"summary": "Add Kubernetes", "details": ["Listed as required"], "impact": "high"}]},
			  {"system": "Greenhouse", "vendor": "Greenhouse", "overallScore": 60, "passesFilter": false}
			], "_model": "openai/gpt-4o-mini"}
			""";

	@Autowired
	private AtsMatchService service;
	@Autowired
	private AtsMatchHandler handler;
	@Autowired
	private JobRepository jobRepository;
	@Autowired
	private VacancyRepository vacancyRepository;
	@Autowired
	private JobService jobService;
	@Autowired
	private ResumeService resumeService;
	@Autowired
	private ResumePdfCacheRepository cacheRepository;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private ObjectMapper objectMapper;
	@MockitoBean
	private AtsScoreClient scoreClient;

	private static final byte[] PDF = {37, 80, 68, 70, 45};

	private long userId;

	@BeforeEach
	void setUp() {
		TestSupport.clearJobs(jobRepository);
		userId = TestSupport.signInNewUser(userRepository);
	}

	@AfterEach
	void signOut() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void reportBecomesTheResultAndScoreIsSavedOnTheVacancy() {
		when(scoreClient.fullScore(any(byte[].class), anyString()))
				.thenReturn(new ScoreResult.Report(objectMapper.readTree(TWO_PLATFORMS)));
		long vacancyId = newVacancy(userId);
		service.enqueue(new MatchRequest("Backend engineer, Kubernetes required", vacancyId, builtPdf()));

		JobOutcome outcome = handler.run(claim());

		JobOutcome.Succeeded succeeded = assertInstanceOf(JobOutcome.Succeeded.class, outcome);
		assertEquals(70, objectMapper.readTree(succeeded.result()).path("averageScore").asInt());
		assertEquals("openai/gpt-4o-mini", objectMapper.readTree(succeeded.result()).path("provider").asString());
		assertEquals(70, vacancyRepository.findById(vacancyId).orElseThrow().getMatchPercent());
	}

	@Test
	void vacancyDeletedWhileQueuedIsSkipped() {
		when(scoreClient.fullScore(any(byte[].class), anyString()))
				.thenReturn(new ScoreResult.Report(objectMapper.readTree(TWO_PLATFORMS)));
		long vacancyId = newVacancy(userId);
		service.enqueue(new MatchRequest("Backend engineer", vacancyId, builtPdf()));
		vacancyRepository.deleteById(vacancyId);

		assertInstanceOf(JobOutcome.Succeeded.class, handler.run(claim()));
	}

	@Test
	void anotherUsersVacancyIsRejectedAtEnqueue() {
		long strangersVacancy = newVacancy(TestSupport.newUser(userRepository));

		assertThrows(NotFoundException.class,
				() -> service.enqueue(new MatchRequest("Backend engineer", strangersVacancy, builtPdf())));
	}

	@Test
	void scoreIsSavedOnlyOnTheJobOwnersVacancy() {
		when(scoreClient.fullScore(any(byte[].class), anyString()))
				.thenReturn(new ScoreResult.Report(objectMapper.readTree(TWO_PLATFORMS)));
		long vacancyId = newVacancy(userId);
		service.enqueue(new MatchRequest("Backend engineer", vacancyId, builtPdf()));
		// The vacancy changes hands while the job waits in the queue.
		Vacancy vacancy = vacancyRepository.findById(vacancyId).orElseThrow();
		vacancy.setUserId(TestSupport.newUser(userRepository));
		vacancyRepository.save(vacancy);

		handler.run(claim());

		assertNull(vacancyRepository.findById(vacancyId).orElseThrow().getMatchPercent());
	}

	@Test
	void scoreFailureIsPermanentAndUnreachableWorkerIsRetried() {
		service.enqueue(new MatchRequest("Backend engineer", null, builtPdf()));
		Job job = claim();

		String message = "The ATS score could not be computed. Please try again later.";
		when(scoreClient.fullScore(any(byte[].class), anyString())).thenReturn(new ScoreResult.Failed(message));
		assertEquals(new JobOutcome.Failed("ats_failed", message), handler.run(job));

		when(scoreClient.fullScore(any(byte[].class), anyString())).thenReturn(new ScoreResult.Unavailable("refused"));
		assertInstanceOf(JobOutcome.Retry.class, handler.run(job));
	}

	@Test
	void whatTheModelAddsToTheStoredResultIsBounded() {
		StringBuilder suggestions = new StringBuilder();
		for (int i = 0; i < 40; i++) {
			suggestions.append(i == 0 ? "" : ",").append("{\"summary\": \"s").append(i).append(" ").append("x".repeat(3000))
					.append("\", \"details\": [\"d\"], \"impact\": \"high\"}");
		}
		String json = "{\"results\": [{\"system\": \"Workday\", \"overallScore\": 80, \"suggestions\": [" + suggestions + "]}]}";
		when(scoreClient.fullScore(any(byte[].class), anyString())).thenReturn(new ScoreResult.Report(objectMapper.readTree(json)));
		service.enqueue(new MatchRequest("Backend engineer", null, builtPdf()));

		JobOutcome.Succeeded succeeded = assertInstanceOf(JobOutcome.Succeeded.class, handler.run(claim()));

		var result = objectMapper.readTree(succeeded.result());
		assertEquals(10, result.path("suggestions").size());
		assertEquals(1000, result.path("suggestions").path(0).path("summary").asString().length());
	}

	/** A finished PDF build of the current resume, with the PDF in the cache. */
	private UUID builtPdf() {
		String hash = Hashes.sha256(resumeService.toTypst());
		cacheRepository.upsert(hash, PDF);
		return UUID.fromString(jobService.enqueueDone(JobType.RESUME_PDF, hash).id());
	}

	@Test
	void theModelIsGivenTheCachedPdf() {
		when(scoreClient.fullScore(any(byte[].class), anyString()))
				.thenReturn(new ScoreResult.Report(objectMapper.readTree(TWO_PLATFORMS)));
		service.enqueue(new MatchRequest("Backend engineer", null, builtPdf()));

		handler.run(claim());

		verify(scoreClient).fullScore(eq(PDF), eq("Backend engineer"));
	}

	@Test
	void aResumeEditedAfterTheBuildOrAPdfNotBuiltYetIsRefused() {
		UUID stale = UUID.fromString(jobService.enqueueDone(JobType.RESUME_PDF, "0".repeat(64)).id());
		UUID running = UUID.fromString(jobService.enqueue(JobType.RESUME_PDF, "1".repeat(64), "source").id());

		assertThrows(ConflictException.class, () -> service.enqueue(new MatchRequest("Backend engineer", null, stale)));
		assertThrows(ConflictException.class, () -> service.enqueue(new MatchRequest("Backend engineer", null, running)));
	}

	@Test
	void aPdfDeletedFromTheCacheFailsTheJob() {
		service.enqueue(new MatchRequest("Backend engineer", null, builtPdf()));
		cacheRepository.deleteUnusedBefore(Instant.now().plusSeconds(60), 100);

		assertEquals(new JobOutcome.Failed("pdf_expired", "The PDF has expired. Build the PDF again."), handler.run(claim()));
	}

	private Job claim() {
		return jobRepository.claimNext(JobType.ATS_MATCH.name(), 60).orElseThrow();
	}

	private long newVacancy(long ownerId) {
		Vacancy vacancy = new Vacancy();
		vacancy.setUserId(ownerId);
		vacancy.setUrl("https://jobs.example.com/" + System.nanoTime());
		vacancy.setTitle("Backend engineer");
		return vacancyRepository.save(vacancy).getId();
	}
}
