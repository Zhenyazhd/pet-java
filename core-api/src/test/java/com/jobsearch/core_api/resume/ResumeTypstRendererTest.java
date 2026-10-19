package com.jobsearch.core_api.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ResumeLatexRendererTest {

	@Test
	void urlKeepsOrdinaryCharacters() {
		String url = "https://www.linkedin.com/in/ada-l_1?x=1&y=%20";
		assertEquals(url, ResumeLatexRenderer.escUrl(url));
		assertEquals("ada@example.com", ResumeLatexRenderer.escUrl("ada@example.com"));
	}

	@Test
	void urlPercentEncodesNonAscii() {
		assertEquals("in/%D0%B5-1", ResumeLatexRenderer.escUrl("in/е-1"));
	}

	@Test
	void urlDropsCharactersThatCouldFormTexTokens() {
		// ^^5c is a backslash and ^^7d a closing brace once TeX reads the argument.
		assertEquals("x5cfoo7ddef", ResumeLatexRenderer.escUrl("x^^5cfoo^^7ddef"));
		assertEquals("abc", ResumeLatexRenderer.escUrl("a\\b{c}$|\n \"#"));
	}
}
