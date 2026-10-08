package com.jobsearch.core_api.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobsearch.core_api.ai.AiDtos.SuggestRequest;
import com.jobsearch.core_api.common.BadRequestException;
import com.jobsearch.core_api.common.ServiceUnavailableException;
import com.jobsearch.core_api.profile.ProfileDtos.ProfileResponse;
import com.jobsearch.core_api.profile.ProfileService;
import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import com.jobsearch.core_api.resume.ResumeService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** How the AI chat turns each {@link ChatResult} into an answer or an HTTP error. */
class ResumeAiServiceTest {

	private static final SuggestRequest HELLO = new SuggestRequest("all", "Hello", null, List.of(), null, null);

	private final OpenRouterClient openRouterClient = mock(OpenRouterClient.class);
	private ResumeAiService service;

	@BeforeEach
	void setUp() {
		ResumeService resumeService = mock(ResumeService.class);
		ProfileService profileService = mock(ProfileService.class);
		when(resumeService.get()).thenReturn(new ResumeDocument(
				"Ada", "Engineer", "", "ada@example.com", "", "", "", List.of(), List.of(), List.of(), List.of(), "en", 1));
		when(profileService.getProfile()).thenReturn(new ProfileResponse("Ada", "ada@example.com", ""));
		service = new ResumeAiService(openRouterClient, resumeService, profileService, JsonMapper.builder().build());
	}

	@Test
	void answerBecomesTheChatMessage() {
		answerWith(new ChatResult.Answer("{\"message\": \"Hi Ada\", \"proposed\": null}"));

		AiDtos.SuggestResponse response = service.suggest(HELLO);

		assertEquals("Hi Ada", response.message());
		assertNull(response.proposed());
	}

	@Test
	void nonJsonAnswerIsServiceUnavailable() {
		answerWith(new ChatResult.Answer("Sorry, I cannot do that."));

		ServiceUnavailableException ex = assertThrows(ServiceUnavailableException.class, () -> service.suggest(HELLO));

		assertEquals("The AI did not answer. Please try again.", ex.getMessage());
	}

	@Test
	void busyIsServiceUnavailableWithRetryAfter() {
		answerWith(new ChatResult.Busy(Duration.ofSeconds(30)));

		ServiceUnavailableException ex = assertThrows(ServiceUnavailableException.class, () -> service.suggest(HELLO));

		assertEquals(Duration.ofSeconds(30), ex.getRetryAfter());
	}

	@Test
	void rejectedRequestIsBadRequest() {
		answerWith(new ChatResult.Rejected("OpenRouter HTTP 400"));

		assertThrows(BadRequestException.class, () -> service.suggest(HELLO));
	}

	@Test
	void misconfigurationAndOutagesAreServiceUnavailableWithoutRetryAfter() {
		answerWith(new ChatResult.Misconfigured("OpenRouter HTTP 401"));
		assertNull(assertThrows(ServiceUnavailableException.class, () -> service.suggest(HELLO)).getRetryAfter());

		answerWith(new ChatResult.Unavailable("OpenRouter HTTP 502"));
		assertNull(assertThrows(ServiceUnavailableException.class, () -> service.suggest(HELLO)).getRetryAfter());
	}

	private void answerWith(ChatResult result) {
		when(openRouterClient.complete(anyString(), anyString(), any())).thenReturn(result);
	}
}
