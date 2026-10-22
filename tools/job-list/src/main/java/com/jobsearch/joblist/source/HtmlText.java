package com.jobsearch.joblist.source;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/** Turns a posting's description (HTML or plain text) into capped plain text with sensible line breaks. */
public final class HtmlText {

	private HtmlText() {
	}

	public static String clean(String html, int maxChars) {
		if (html == null || html.isBlank()) {
			return "";
		}
		Document document = Jsoup.parse(html);
		document.select("p, li, div, h1, h2, h3, h4, tr").after("\\n");
		String text = document.wholeText().replace("\\n", "\n").replace(' ', ' ');
		String collapsed = text.replaceAll("[ \\t]+", " ").replaceAll(" ?\\n ?", "\n").replaceAll("\\n{3,}", "\n\n").strip();
		return collapsed.length() <= maxChars ? collapsed : collapsed.substring(0, maxChars).strip();
	}
}
