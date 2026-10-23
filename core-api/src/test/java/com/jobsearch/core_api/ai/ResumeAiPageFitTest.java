package com.jobsearch.core_api.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobsearch.core_api.ai.AiDtos.SuggestRequest;
import com.jobsearch.core_api.ai.AiDtos.SuggestResponse;
import com.jobsearch.core_api.compile.PageLayout;
import com.jobsearch.core_api.compile.ResumeLayoutService;
import com.jobsearch.core_api.profile.ProfileDtos.ProfileResponse;
import com.jobsearch.core_api.profile.ProfileService;
import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import com.jobsearch.core_api.resume.ResumeService;
import jakarta.validation.Validation;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

/** A whole-CV proposal is measured on the page and corrected by the model until it fits, a few rounds at most. */
class ResumeAiPageFitTest {

	private static final SuggestRequest TAILOR = new SuggestRequest("all", "Tailor it", null, List.of(), null, null);

	/** One page, 97% full, nominal layout: the right length. */
	private static final PageLayout FITS = new PageLayout(1, 0.97, 1.0);
	/** Two pages at the tightest layout: far too long. */
	private static final PageLayout WAY_OVER = new PageLayout(2, 0.30, 0.88);
	/** One page, but only by tightening the layout: a little too long. */
	private static final PageLayout A_BIT_OVER = new PageLayout(1, 1.0, 0.90);

	private final OpenRouterClient openRouterClient = mock(OpenRouterClient.class);
	private final ResumeLayoutService layoutService = mock(ResumeLayoutService.class);
	private ResumeAiService service;

	@BeforeEach
	void setUp() {
		ResumeService resumeService = mock(ResumeService.class);
		ProfileService profileService = mock(ProfileService.class);
		when(resumeService.get()).thenReturn(new ResumeDocument(
				"Ada", "Engineer", "", "ada@example.com", "", "", "", List.of(), List.of(), List.of(), List.of(), "en", 1));
		when(profileService.getProfile()).thenReturn(new ProfileResponse("Ada", "ada@example.com", ""));
		JsonMapper mapper = JsonMapper.builder().build();
		service = new ResumeAiService(openRouterClient, resumeService, profileService, mock(UserPromptService.class), mapper,
				new ProposedValidator(mapper, Validation.buildDefaultValidatorFactory().getValidator()), layoutService);
	}

	@Test
	void aProposalThatFitsIsReturnedWithoutAnotherCall() {
		models(reply("First", "Done"));
		layouts(FITS, FITS); // the saved CV for the prompt, then the proposal

		SuggestResponse response = service.suggest(TAILOR);

		assertEquals("First", headline(response));
		verify(openRouterClient, times(1)).complete(anyString(), anyString(), any());
	}

	@Test
	void anOverflowIsSentBackWithItsMeasurementAndTheCorrectionIsUsed() {
		models(reply("Long", "Here"), reply("Short", "Cut two bullets for the page"));
		layouts(FITS, WAY_OVER, FITS);

		SuggestResponse response = service.suggest(TAILOR);

		assertEquals("Short", headline(response));
		assertEquals("Here\n\nCut two bullets for the page", response.message());
		ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
		verify(openRouterClient, times(2)).complete(anyString(), prompts.capture(), any());
		String correction = prompts.getAllValues().get(1);
		assertTrue(correction.contains("<previous_proposal>"), correction);
		assertTrue(correction.contains("\"headline\":\"Long\""), correction);
		assertTrue(correction.contains("lines over one page"), correction);
	}

	@Test
	void whenItNeverFitsTheClosestCandidateIsKept() {
		models(reply("Way over", "one"), reply("A bit over", "two"), reply("Way over again", "three"));
		layouts(FITS, WAY_OVER, A_BIT_OVER, WAY_OVER);

		SuggestResponse response = service.suggest(TAILOR);

		assertEquals("A bit over", headline(response));
		assertEquals("one\n\ntwo", response.message());
		verify(openRouterClient, times(3)).complete(anyString(), anyString(), any()); // 1 + 2 correction rounds
	}

	@Test
	void aProposalMarkedFitPageFalseIsNotMeasured() {
		models("{\"message\": \"Translated\", \"fit_page\": false, \"proposed\": " + resume("Ingenieure") + "}");
		layouts(FITS);

		SuggestResponse response = service.suggest(TAILOR);

		assertEquals("Ingenieure", headline(response));
		verify(openRouterClient, times(1)).complete(anyString(), anyString(), any());
		verify(layoutService, times(1)).of(any()); // only the saved CV, for the prompt
	}

	@Test
	void aSectionEditIsNeverMeasured() {
		models("{\"message\": \"Done\", \"proposed\": \"A new profile text.\"}");

		service.suggest(new SuggestRequest("profile", "Rewrite it", null, List.of(), null, null));

		verify(layoutService, never()).of(any());
	}

	@Test
	void withoutAWorkerTheProposalIsKeptAsIs() {
		models(reply("First", "Done"));
		when(layoutService.of(any())).thenReturn(Optional.empty());

		SuggestResponse response = service.suggest(TAILOR);

		assertEquals("First", headline(response));
		verify(openRouterClient, times(1)).complete(anyString(), anyString(), any());
	}

	@Test
	void aBrokenCorrectionKeepsThePreviousProposal() {
		models(reply("Long", "Here"), "not json at all");
		layouts(FITS, WAY_OVER);

		SuggestResponse response = service.suggest(TAILOR);

		assertEquals("Long", headline(response));
		assertEquals("Here", response.message());
	}

	@Test
	void theLengthIsReadAsLinesOverOrUnderTheNominalLayout() {
		assertTrue(PageFit.fits(new PageLayout(1, 0.97, 1.0)));
		assertTrue(PageFit.fits(new PageLayout(1, 1.0, 1.0)));
		assertTrue(!PageFit.fits(new PageLayout(1, 0.6, 1.0))); // room for ~22 lines
		assertTrue(PageFit.lines(new PageLayout(1, 0.6, 1.0)) < -4);
		assertTrue(PageFit.lines(new PageLayout(2, 0.3, 0.88)) > 4);
		assertTrue(PageFit.distance(A_BIT_OVER) < PageFit.distance(WAY_OVER));
	}

	private void models(String first, String... more) {
		var stubbing = when(openRouterClient.complete(anyString(), anyString(), any())).thenReturn(new ChatResult.Answer(first));
		for (String next : more) {
			stubbing = stubbing.thenReturn(new ChatResult.Answer(next));
		}
	}

	/** The saved CV is measured first (for the prompt), then each proposal in turn. */
	private void layouts(PageLayout first, PageLayout... more) {
		var stubbing = when(layoutService.of(any())).thenReturn(Optional.of(first));
		for (PageLayout next : more) {
			stubbing = stubbing.thenReturn(Optional.of(next));
		}
	}

	private static String reply(String headline, String message) {
		return "{\"message\": \"" + message + "\", \"proposed\": " + resume(headline) + "}";
	}

	private static String resume(String headline) {
		return "{\"name\":\"Ada\",\"headline\":\"" + headline + "\",\"phone\":\"\",\"email\":\"ada@example.com\","
				+ "\"linkedinUrl\":\"\",\"linkedinLabel\":\"\",\"profile\":\"\",\"experience\":[],\"education\":[],"
				+ "\"achievements\":[],\"skills\":[],\"locale\":\"en\",\"version\":1}";
	}

	private static String headline(SuggestResponse response) {
		return response.proposed().path("headline").asString();
	}
}
