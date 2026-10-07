package com.jobsearch.core_api.ats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.jobsearch.core_api.TestSupport;
import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.ats.AtsDtos.MatchRequest;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.compile.ResumePdfCacheRepository;
import com.jobsearch.core_api.jobs.Job;
import com.jobsearch.core_api.jobs.JobOutcome;
import com.jobsearch.core_api.jobs.JobRepository;
import com.jobsearch.core_api.jobs.JobType;
import com.jobsearch.core_api.profile.AppUser;
import com.jobsearch.core_api.profile.AppUserRepository;
import java.util.UUID;
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

/** Queueing a match and running it through {@link AtsMatchHandler}, with a mocked ATS Screener. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AtsMatchTest {

	private static final String TWO_PLATFORMS = """
			{"results": [
			  {"system": "Workday", "vendor": "Workday", "overallScore": 80, "passesFilter": true,
			   "suggestions": [{"summary": "Add Kubernetes", "details": ["Listed as required"], "impact": "high"}]},
			  {"system": "Greenhouse", "vendor": "Greenhouse", "overallScore": 60, "passesFilter": false}
			], "_provider": "gemini", "_cached": false}
			""";

	@Autowired
	private AtsMatchService service;
	@Autowired
	private AtsMatchHandler handler;
	@Autowired
	private JobRepository jobRepository;
	@Autowired
	private ResumePdfCacheRepository cacheRepository;
	@Autowired
	private VacancyRepository vacancyRepository;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private ObjectMapper objectMapper;
	@MockitoBean
	private AtsScreenerClient screenerClient;

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
	void reportBecomesTheResultAndScoreIsSavedOnTheVacancy() {
		when(screenerClient.fullScore(anyString(), anyString()))
				.thenReturn(new ScreenerResult.Report(objectMapper.readTree(TWO_PLATFORMS)));
		long vacancyId = newVacancy(userId);
		service.enqueue(new MatchRequest("Backend engineer, Kubernetes required", vacancyId));

		JobOutcome outcome = handler.run(claim());

		JobOutcome.Succeeded succeeded = assertInstanceOf(JobOutcome.Succeeded.class, outcome);
		assertEquals(70, objectMapper.readTree(succeeded.result()).path("averageScore").asInt());
		assertEquals(70, vacancyRepository.findById(vacancyId).orElseThrow().getMatchPercent());
	}

	@Test
	void vacancyDeletedWhileQueuedIsSkipped() {
		when(screenerClient.fullScore(anyString(), anyString()))
				.thenReturn(new ScreenerResult.Report(objectMapper.readTree(TWO_PLATFORMS)));
		long vacancyId = newVacancy(userId);
		service.enqueue(new MatchRequest("Backend engineer", vacancyId));
		vacancyRepository.deleteById(vacancyId);

		assertInstanceOf(JobOutcome.Succeeded.class, handler.run(claim()));
	}

	@Test
	void anotherUsersVacancyIsRejectedAtEnqueue() {
		long strangersVacancy = newVacancy(newUserId());

		assertThrows(NotFoundException.class,
				() -> service.enqueue(new MatchRequest("Backend engineer", strangersVacancy)));
	}

	@Test
	void scoreIsSavedOnlyOnTheJobOwnersVacancy() {
		when(screenerClient.fullScore(anyString(), anyString()))
				.thenReturn(new ScreenerResult.Report(objectMapper.readTree(TWO_PLATFORMS)));
		long vacancyId = newVacancy(userId);
		service.enqueue(new MatchRequest("Backend engineer", vacancyId));
		// The vacancy changes hands while the job waits in the queue.
		Vacancy vacancy = vacancyRepository.findById(vacancyId).orElseThrow();
		vacancy.setUserId(newUserId());
		vacancyRepository.save(vacancy);

		handler.run(claim());

		assertNull(vacancyRepository.findById(vacancyId).orElseThrow().getMatchPercent());
	}

	@Test
	void screenerFailureIsPermanentAndUnreachableScreenerIsRetried() {
		service.enqueue(new MatchRequest("Backend engineer", null));
		Job job = claim();

		when(screenerClient.fullScore(anyString(), anyString())).thenReturn(new ScreenerResult.Failed("quota"));
		assertEquals(new JobOutcome.Failed("ats_failed", "quota"), handler.run(job));

		when(screenerClient.fullScore(anyString(), anyString())).thenReturn(new ScreenerResult.Unavailable("refused"));
		assertInstanceOf(JobOutcome.Retry.class, handler.run(job));
	}

	private Job claim() {
		return jobRepository.claimNext(JobType.ATS_MATCH.name(), 60).orElseThrow();
	}

	/** A user who exists but is not signed in. */
	private long newUserId() {
		AppUser user = new AppUser();
		user.setEmail(UUID.randomUUID() + "@test.local");
		user.setDisplayName("Someone else");
		return userRepository.save(user).getId();
	}

	private long newVacancy(long ownerId) {
		Vacancy vacancy = new Vacancy();
		vacancy.setUserId(ownerId);
		vacancy.setUrl("https://jobs.example.com/" + System.nanoTime());
		vacancy.setTitle("Backend engineer");
		return vacancyRepository.save(vacancy).getId();
	}
}
