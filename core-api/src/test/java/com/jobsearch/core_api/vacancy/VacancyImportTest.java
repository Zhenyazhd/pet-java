package com.jobsearch.core_api.vacancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jobsearch.core_api.TestSupport;
import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.ai.ChatResult;
import com.jobsearch.core_api.ai.OpenRouterClient;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.jobs.Job;
import com.jobsearch.core_api.jobs.JobDtos.JobResponse;
import com.jobsearch.core_api.jobs.JobOutcome;
import com.jobsearch.core_api.jobs.JobRepository;
import com.jobsearch.core_api.jobs.JobType;
import com.jobsearch.core_api.profile.AppUserRepository;
import com.jobsearch.core_api.vacancy.VacancyDtos.VacancyImportRequest;
import java.time.Duration;
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

/** Queueing an import and running it through {@link VacancyImportHandler}, with a mocked LLM. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class VacancyImportTest {

	private static final String URL = "https://jobs.example.com/backend";
	private static final String EXTRACTED = """
			{"title": "Backend Engineer", "company": "Acme",
			 "description": "Build APIs.",
			 "requirements": [{"name": "Java", "required": true}, {"name": "Kafka", "required": false}]}
			""";

	@Autowired
	private VacancyImportService service;
	@Autowired
	private VacancyImportHandler handler;
	@Autowired
	private JobRepository jobRepository;
	@Autowired
	private VacancyRepository vacancyRepository;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private ObjectMapper objectMapper;
	@MockitoBean
	private OpenRouterClient openRouterClient;

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
	void extractedVacancyIsSavedAndItsIdIsTheResult() {
		when(openRouterClient.complete(anyString(), anyString(), any())).thenReturn(new ChatResult.Answer(EXTRACTED));
		service.enqueue(new VacancyImportRequest(URL, "Backend Engineer at Acme ..."));

		JobOutcome.Succeeded outcome = assertInstanceOf(JobOutcome.Succeeded.class, handler.run(claim()));

		long vacancyId = objectMapper.readTree(outcome.result()).path("vacancyId").asLong();
		Vacancy vacancy = vacancyRepository.findDetailedByIdAndUserId(vacancyId, userId).orElseThrow();
		assertEquals("Backend Engineer", vacancy.getTitle());
		assertEquals("Acme", vacancy.getCompany());
		assertEquals(2, vacancy.getRequirements().size());
	}

	@Test
	void doubleSubmitReturnsTheSameJob() {
		JobResponse first = service.enqueue(new VacancyImportRequest(URL, "posting"));
		JobResponse second = service.enqueue(new VacancyImportRequest(URL, "posting"));

		assertEquals(first.id(), second.id());
	}

	@Test
	void existingUrlIsRejectedAtEnqueue() {
		saveVacancy(URL);

		assertThrows(ConflictException.class, () -> service.enqueue(new VacancyImportRequest(URL, "posting")));
	}

	@Test
	void vacancySavedBeforeTheJobRunsIsTheResultWithoutCallingTheLlm() {
		service.enqueue(new VacancyImportRequest(URL, "posting"));
		// Saved by an earlier attempt whose outcome was lost, or by the user while the job waited.
		long savedId = saveVacancy(URL);

		JobOutcome.Succeeded outcome = assertInstanceOf(JobOutcome.Succeeded.class, handler.run(claim()));

		assertEquals(savedId, objectMapper.readTree(outcome.result()).path("vacancyId").asLong());
		verifyNoInteractions(openRouterClient);
	}

	@Test
	void vacancySavedWhileTheLlmAnswersIsTheResult() {
		service.enqueue(new VacancyImportRequest(URL, "posting"));
		long[] savedDuringLlmCall = new long[1];
		when(openRouterClient.complete(anyString(), anyString(), any())).thenAnswer(call -> {
			savedDuringLlmCall[0] = saveVacancy(URL);
			return new ChatResult.Answer(EXTRACTED);
		});

		JobOutcome.Succeeded outcome = assertInstanceOf(JobOutcome.Succeeded.class, handler.run(claim()));

		assertEquals(savedDuringLlmCall[0], objectMapper.readTree(outcome.result()).path("vacancyId").asLong());
		assertEquals(1, vacancyRepository.findAllByUserIdOrderByCreatedAtDesc(userId).size());
	}

	@Test
	void unavailableLlmIsRetried() {
		assertInstanceOf(JobOutcome.Retry.class, runWithLlmResult(new ChatResult.Unavailable("OpenRouter HTTP 502")));
	}

	@Test
	void busyLlmPausesImportsWithoutUsingAnAttempt() {
		JobOutcome outcome = runWithLlmResult(new ChatResult.Busy(Duration.ofSeconds(30)));

		assertEquals(Duration.ofSeconds(30), assertInstanceOf(JobOutcome.Busy.class, outcome).retryAfter());
	}

	@Test
	void misconfiguredLlmFailsWithoutRetry() {
		JobOutcome outcome = runWithLlmResult(new ChatResult.Misconfigured("OpenRouter HTTP 401"));

		assertEquals("ai_unavailable", assertInstanceOf(JobOutcome.Failed.class, outcome).code());
	}

	@Test
	void rejectedLlmFailsWithoutRetry() {
		JobOutcome outcome = runWithLlmResult(new ChatResult.Rejected("answer cut off at max_tokens"));

		assertEquals("ai_rejected", assertInstanceOf(JobOutcome.Failed.class, outcome).code());
	}

	private JobOutcome runWithLlmResult(ChatResult result) {
		when(openRouterClient.complete(anyString(), anyString(), any())).thenReturn(result);
		service.enqueue(new VacancyImportRequest(URL, "posting"));
		return handler.run(claim());
	}

	private Job claim() {
		return jobRepository.claimNext(JobType.VACANCY_IMPORT.name(), 60).orElseThrow();
	}

	private long saveVacancy(String url) {
		Vacancy vacancy = new Vacancy();
		vacancy.setUserId(userId);
		vacancy.setUrl(url);
		vacancy.setTitle("Saved by hand");
		return vacancyRepository.save(vacancy).getId();
	}
}
