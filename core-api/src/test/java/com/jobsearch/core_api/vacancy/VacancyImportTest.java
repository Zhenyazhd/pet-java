package com.jobsearch.core_api.vacancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.jobsearch.core_api.TestSupport;
import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.ai.OpenRouterClient;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.jobs.Job;
import com.jobsearch.core_api.jobs.JobDtos.JobResponse;
import com.jobsearch.core_api.jobs.JobOutcome;
import com.jobsearch.core_api.jobs.JobRepository;
import com.jobsearch.core_api.jobs.JobType;
import com.jobsearch.core_api.profile.AppUserRepository;
import com.jobsearch.core_api.vacancy.VacancyDtos.VacancyImportRequest;
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
		when(openRouterClient.chat(anyString(), anyString(), any())).thenReturn(EXTRACTED);
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
	void urlSavedWhileQueuedFailsTheJob() {
		when(openRouterClient.chat(anyString(), anyString(), any())).thenReturn(EXTRACTED);
		service.enqueue(new VacancyImportRequest(URL, "posting"));
		saveVacancy(URL);

		JobOutcome.Failed outcome = assertInstanceOf(JobOutcome.Failed.class, handler.run(claim()));

		assertEquals("duplicate_url", outcome.code());
	}

	@Test
	void llmFailureIsRetried() {
		when(openRouterClient.chat(anyString(), anyString(), any()))
				.thenThrow(new IllegalStateException("OpenRouter error 502"));
		service.enqueue(new VacancyImportRequest(URL, "posting"));

		assertInstanceOf(JobOutcome.Retry.class, handler.run(claim()));
	}

	private Job claim() {
		return jobRepository.claimNext(JobType.VACANCY_IMPORT.name(), 60).orElseThrow();
	}

	private void saveVacancy(String url) {
		Vacancy vacancy = new Vacancy();
		vacancy.setUserId(userId);
		vacancy.setUrl(url);
		vacancy.setTitle("Saved by hand");
		vacancyRepository.save(vacancy);
	}
}
