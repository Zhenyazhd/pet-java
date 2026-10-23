package com.jobsearch.core_api.ai;

import com.jobsearch.core_api.compile.PageLayout;

/**
 * Reads a measured {@link PageLayout} the way the chat prompt does: how many lines the text is over or under what
 * the nominal layout fits on one page. The pdf worker tightens or loosens the typography to land on one page, so
 * "fits" does not mean the PDF has one page, it means the text length is right for the nominal layout.
 */
final class PageFit {

	static final int LINES_PER_PAGE = 55;
	static final int TOLERANCE_LINES = 4;

	private PageFit() {
	}

	/** Positive: lines over one page at the nominal layout. Negative: room left. Type scales with the square of the layout scale. */
	static int lines(PageLayout layout) {
		double scale = layout.scale() == null || layout.scale() <= 0 ? 1.0 : layout.scale();
		double nominalPages = ((layout.pages() - 1) + layout.fill()) / (scale * scale);
		return (int) Math.round((nominalPages - 1) * LINES_PER_PAGE);
	}

	static boolean fits(PageLayout layout) {
		return layout.pages() == 1 && Math.abs(lines(layout)) <= TOLERANCE_LINES;
	}

	/** Lower is better; any one-page layout beats any overflow. */
	static double distance(PageLayout layout) {
		return (layout.pages() > 1 ? 1000 : 0) + Math.abs(lines(layout));
	}

	/** The facts and the verdict, as lines for the prompt. */
	static String describe(PageLayout layout) {
		int lines = lines(layout);
		String scale = layout.scale() == null
				? "not reported"
				: String.format(java.util.Locale.ROOT, "%.2f (1.00 is the nominal layout)", layout.scale());
		String verdict = fits(layout)
				? "The length is right for the nominal layout."
				: lines > 0
						? "The text is about " + lines + " lines over one page at the nominal layout: cut that many lines."
						: "There is room for about " + (-lines) + " more lines (about " + (-lines + 1) / 2
								+ " bullet points of two lines) at the nominal layout.";
		return "- Pages: " + layout.pages() + ", last page " + Math.round(layout.fill() * 100) + "% full.\n"
				+ "- Layout scale: " + scale + ".\n"
				+ "- " + verdict;
	}
}
