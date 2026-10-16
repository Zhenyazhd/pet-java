package com.jobsearch.joblist.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HtmlTextTest {

	@Test
	void blocksBecomeLinesAndEntitiesAreDecoded() {
		String text = HtmlText.clean("<p>Build&nbsp;APIs &amp; tools.</p><ul><li>Java</li><li>SQL</li></ul>Line<br>break", 1000);

		assertEquals("Build APIs & tools.\nJava\nSQL\nLine\nbreak", text);
	}

	@Test
	void plainTextAndBlankInputAreHandled() {
		assertEquals("Just   text".replaceAll(" +", " "), HtmlText.clean("Just   text", 1000));
		assertEquals("", HtmlText.clean("  ", 1000));
		assertEquals("", HtmlText.clean(null, 1000));
	}

	@Test
	void longTextIsCut() {
		assertEquals("abcde", HtmlText.clean("abcdefghij", 5));
	}
}
