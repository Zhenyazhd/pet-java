package com.jobsearch.pdfworker;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * How many pages a PDF has and how full its last page is.
 *
 * <p>{@code fill} is the vertical extent of the text on the last page (first line top to last baseline) divided by
 * the height of the text area. The text area is taken as the page minus a margin of the same size above and below,
 * the margin being where the text starts: the template's top and bottom margins are almost equal, and the worker
 * has no other way to know them. It is a measure for steering a page to "full", not an exact layout value; a page
 * with no text counts as {@code 0}. Page numbers and other footer text count as text, so the document must use
 * {@code numbering: none} (the resume template does). Descenders are ignored, so a full page measures about 0.99.
 */
record PageMetrics(int pages, double fill) {

	static PageMetrics measure(byte[] pdf) throws IOException {
		try (PDDocument document = Loader.loadPDF(pdf)) {
			int pages = document.getNumberOfPages();
			if (pages == 0) {
				return new PageMetrics(0, 0);
			}
			PDPage page = document.getPage(pages - 1);
			// Glyph coordinates are relative to the CropBox and already rotated by /Rotate.
			float pageHeight = page.getRotation() % 180 == 0 ? page.getCropBox().getHeight() : page.getCropBox().getWidth();
			Extent extent = new Extent();
			extent.setStartPage(pages);
			extent.setEndPage(pages);
			extent.getText(document);
			return new PageMetrics(pages, fill(extent.top, extent.bottom, pageHeight));
		}
	}

	static double fill(double top, double bottom, double pageHeight) {
		double area = pageHeight - 2 * top;
		if (top > bottom || area <= 0) {
			return 0;
		}
		return Math.min(1, (bottom - top) / area);
	}

	/** Collects the highest top and the lowest baseline of the glyphs, measured from the top of the page. */
	private static final class Extent extends PDFTextStripper {

		double top = Double.MAX_VALUE;
		double bottom = -Double.MAX_VALUE;

		Extent() throws IOException {
		}

		@Override
		protected void writeString(String text, List<TextPosition> positions) {
			for (TextPosition position : positions) {
				top = Math.min(top, position.getYDirAdj() - position.getHeightDir());
				bottom = Math.max(bottom, position.getYDirAdj());
			}
		}

		@Override
		protected void writeString(String text) {
		}
	}
}
