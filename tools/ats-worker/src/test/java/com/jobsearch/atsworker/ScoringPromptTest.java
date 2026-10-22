package com.jobsearch.atsworker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScoringPromptTest {

	@Test
	void aVacancyMakesItTargetedScoring() {
		String prompt = ScoringPrompt.build("Jane Doe, Java developer", "We need Kubernetes experience");

		assertTrue(prompt.contains("<RESUME>\nJane Doe, Java developer\n</RESUME>"));
		assertTrue(prompt.contains("<JOB_DESCRIPTION>\nWe need Kubernetes experience\n</JOB_DESCRIPTION>"));
		assertTrue(prompt.contains("MODE: targeted scoring"));
		assertTrue(prompt.contains("how well resume keywords match the JD requirements"));
		assertTrue(prompt.contains("keyword match scores should be the MOST sensitive dimension to JD changes"));
	}

	@Test
	void withoutAVacancyItIsGeneralScoring() {
		String prompt = ScoringPrompt.build("Jane Doe, Java developer", "  ");

		assertTrue(prompt.contains("MODE: general ATS readiness"));
		assertFalse(prompt.contains("<JOB_DESCRIPTION>"));
		assertTrue(prompt.contains("keyword density and professional terminology quality"));
		assertFalse(prompt.contains("MOST sensitive dimension"));
		// the dropped bullet leaves no empty "- " line behind
		assertFalse(prompt.contains("\n- \n"));
	}

	@Test
	void everyPlaceholderIsFilledAndNothingOfOursIsLeftOver() {
		for (String vacancy : new String[] {"", "A vacancy"}) {
			String prompt = ScoringPrompt.build("A resume", vacancy);

			assertFalse(prompt.contains("{{"), "unfilled placeholder in the " + (vacancy.isEmpty() ? "general" : "targeted") + " prompt");
			assertFalse(prompt.contains("${"));
			assertTrue(prompt.contains("Return exactly 6 results in order: Workday, Taleo, iCIMS, Greenhouse, Lever, SuccessFactors."));
		}
	}

	@Test
	void placeholdersInsideUserTextAreNotExpanded() {
		String prompt = ScoringPrompt.build("my {{JOB_DESCRIPTION}} and $1 and \\n", "vacancy {{RESUME}}");

		assertTrue(prompt.contains("my {{JOB_DESCRIPTION}} and $1 and \\n"));
		assertTrue(prompt.contains("vacancy {{RESUME}}"));
	}

	@Test
	void longTextIsCutToTheSameSizesAsInAtsScreener() {
		String prompt = ScoringPrompt.build("r".repeat(9000), "v".repeat(9000));

		assertTrue(prompt.contains("r".repeat(ScoringPrompt.RESUME_CHARS)));
		assertFalse(prompt.contains("r".repeat(ScoringPrompt.RESUME_CHARS + 1)));
		assertTrue(prompt.contains("v".repeat(ScoringPrompt.JOB_DESCRIPTION_CHARS)));
		assertFalse(prompt.contains("v".repeat(ScoringPrompt.JOB_DESCRIPTION_CHARS + 1)));
	}
}
