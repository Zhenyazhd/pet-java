package com.jobsearch.atsworker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextNormTest {

	@Test
	void tokensIgnorePunctuationCaseAndLigatures() {
		assertEquals(List.of("final", "node", "js", "опыт"), TextNorm.tokens("\uFB01nal, Node.js — ОПЫТ"));
	}

	@Test
	void squashIgnoresSpacingAndHyphens() {
		assertEquals("infrastructure", TextNorm.squash("infra-\nstructure"));
		assertEquals("experience", TextNorm.squash("E X P E R I E N C E"));
	}

	@Test
	void headingsAreMatchedWhateverTheCaseOrLanguage() {
		assertEquals(List.of("experience", "education", "skills"), Sections.find("WORK EXPERIENCE\nx\nОбразование\nTechnical Skills"));
	}
}
