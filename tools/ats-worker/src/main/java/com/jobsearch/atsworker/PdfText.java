package com.jobsearch.atsworker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.cos.COSName;

/** Reads the text layer of a PDF the way a parser would: in content-stream order and in visual order. */
final class PdfText {

	private static final int MAX_GLYPHS = 300_000;
	/** A resume page is A4 or Letter (about 600 x 850 pt); anything beyond this is not one (and costs memory). */
	private static final double MAX_PAGE_POINTS = 5000;
	private static final int MIN_GLYPHS_FOR_COLUMNS = 200;
	private static final double ROW_HEIGHT_POINTS = 3;
	/** Where on the page width a gutter between two columns is looked for. */
	private static final double GUTTER_SEARCH_FROM = 0.25;
	private static final double GUTTER_SEARCH_TO = 0.75;
	private static final int MAX_IMAGE_SIDE_PIXELS = 50;
	/** A vertical strip this wide with no text through it separates two columns (LaTeX's default 10 pt column gap measures about 7 pt between glyphs). */
	private static final double GUTTER_POINTS = 5;
	/** Share of text rows that may cross a gutter (a full-width name or title line). */
	private static final double GUTTER_CROSSING_ROWS = 0.15;
	private static final double GUTTER_MIN_SIDE_SHARE = 0.15;
	/** A page with fewer text rows than this has too little to show a gutter (a short resume with a table). */
	private static final int MIN_ROWS_FOR_COLUMNS = 20;

	private PdfText() {
	}

	/**
	 * @param stream  text in the order it was drawn (what a naive parser reads)
	 * @param visual  text sorted by position (what a layout-aware parser reads; interleaves columns)
	 * @param columns a page has two text columns
	 * @param largeImages images big enough to be a photo or a picture of text
	 */
	record Extracted(String stream, String visual, int pages, boolean columns, int largeImages) {
	}

	private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

	static boolean looksLikePdf(byte[] bytes) {
		return bytes.length >= PDF_MAGIC.length
				&& Arrays.equals(bytes, 0, PDF_MAGIC.length, PDF_MAGIC, 0, PDF_MAGIC.length);
	}

	/** Just the text in content-stream order: what {@code /score} needs, without the layout analysis of {@link #read}. */
	static String readStreamText(byte[] pdf, int maxPages, Deadline deadline) throws IOException {
		return open(pdf, maxPages, deadline, false).stream();
	}

	static Extracted read(byte[] pdf, int maxPages, Deadline deadline) throws IOException {
		return open(pdf, maxPages, deadline, true);
	}

	private static Extracted open(byte[] pdf, int maxPages, Deadline deadline, boolean analyseLayout) throws IOException {
		try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdf), "", null, null,
				IOUtils.createTempFileOnlyStreamCache())) {
			int pages = document.getNumberOfPages();
			if (pages > maxPages) {
				throw new CheckFailure(422, "too_many_pages", "The PDF has " + pages + " pages, the limit is " + maxPages);
			}
			for (PDPage page : document.getPages()) {
				PDRectangle box = page.getMediaBox();
				if (box.getWidth() > MAX_PAGE_POINTS || box.getHeight() > MAX_PAGE_POINTS) {
					throw new CheckFailure(422, "page_too_large", "A page is larger than a resume page can be");
				}
			}
			Stripper stream = new Stripper(deadline, analyseLayout);
			stream.setSortByPosition(false);
			String streamText = stream.getText(document);
			if (!analyseLayout) {
				return new Extracted(streamText, "", pages, false, 0);
			}

			Stripper visual = new Stripper(deadline, false);
			visual.setSortByPosition(true);
			String visualText = visual.getText(document);

			return new Extracted(streamText, visualText, pages, hasColumns(stream.glyphs), largeImages(document, deadline));
		}
		catch (InvalidPasswordException ex) {
			throw new CheckFailure(422, "encrypted", "The PDF is password protected, an ATS cannot read it");
		}
	}

	private static int largeImages(PDDocument document, Deadline deadline) throws IOException {
		int count = 0;
		for (PDPage page : document.getPages()) {
			deadline.check();
			PDResources resources = page.getResources();
			if (resources == null) {
				continue;
			}
			// top-level images only: width and height come from the image dictionary, nothing is decoded
			for (COSName name : resources.getXObjectNames()) {
				PDXObject object = resources.getXObject(name);
				if (object instanceof PDImageXObject image
						&& image.getWidth() > MAX_IMAGE_SIDE_PIXELS && image.getHeight() > MAX_IMAGE_SIDE_PIXELS) {
					count++;
				}
			}
		}
		return count;
	}

	private record Glyph(int page, double x, double width, double y, double pageWidth) {
	}

	private static final class Stripper extends PDFTextStripper {

		private final Deadline deadline;
		private final boolean collect;
		private final List<Glyph> glyphs = new ArrayList<>();
		private int seen;

		Stripper(Deadline deadline, boolean collect) throws IOException {
			this.deadline = deadline;
			this.collect = collect;
		}

		@Override
		protected void startPage(PDPage page) throws IOException {
			deadline.check();
			super.startPage(page);
		}

		@Override
		protected void processTextPosition(TextPosition text) {
			if (++seen > MAX_GLYPHS) {
				throw new CheckFailure(422, "too_much_text", "The PDF has more than " + MAX_GLYPHS + " characters");
			}
			if ((seen & 0x3FF) == 0) {
				deadline.check();
			}
			if (collect) {
				glyphs.add(new Glyph(getCurrentPageNo(), text.getXDirAdj(), text.getWidthDirAdj(), text.getYDirAdj(),
						text.getPageWidth()));
			}
			super.processTextPosition(text);
		}
	}

	/**
	 * A column layout leaves a vertical strip, somewhere in the middle of the page, that almost no text row
	 * crosses, with a real share of the text on both sides. Right-aligned dates leave no such strip because
	 * the bullets between them run across the middle.
	 */
	private static boolean hasColumns(List<Glyph> glyphs) {
		Map<Integer, List<Glyph>> byPage = new TreeMap<>();
		for (Glyph glyph : glyphs) {
			byPage.computeIfAbsent(glyph.page(), key -> new ArrayList<>()).add(glyph);
		}
		return byPage.values().stream().anyMatch(PdfText::pageHasColumns);
	}

	private static boolean pageHasColumns(List<Glyph> glyphs) {
		if (glyphs.size() < MIN_GLYPHS_FOR_COLUMNS) {
			return false;
		}
		int width = (int) Math.min(Math.ceil(glyphs.get(0).pageWidth()), MAX_PAGE_POINTS);
		// How many text rows have text through each point of the page width, from per-row merged intervals.
		Map<Long, List<double[]>> rows = new TreeMap<>();
		for (Glyph glyph : glyphs) {
			rows.computeIfAbsent(Math.round(glyph.y() / ROW_HEIGHT_POINTS), key -> new ArrayList<>())
					.add(new double[] {glyph.x(), glyph.x() + glyph.width()});
		}
		if (rows.size() < MIN_ROWS_FOR_COLUMNS) {
			return false;
		}
		int[] crossing = new int[width + 2];
		for (List<double[]> row : rows.values()) {
			addCoverage(row, crossing, width);
		}
		double allowed = GUTTER_CROSSING_ROWS * rows.size();
		int start = (int) (width * GUTTER_SEARCH_FROM);
		int end = (int) (width * GUTTER_SEARCH_TO);
		int runStart = -1;
		for (int x = start; x <= end + 1; x++) {
			boolean open = x <= end && crossing[x] <= allowed;
			if (open && runStart < 0) {
				runStart = x;
			}
			else if (!open && runStart >= 0) {
				if (x - runStart >= GUTTER_POINTS && bothSidesHaveText(glyphs, (runStart + x) / 2.0)) {
					return true;
				}
				runStart = -1;
			}
		}
		return false;
	}

	/** Adds 1 to {@code crossing[x]} for every point x that some text of the row covers (a difference array). */
	private static void addCoverage(List<double[]> intervals, int[] crossing, int width) {
		intervals.sort((first, second) -> Double.compare(first[0], second[0]));
		int[] diff = new int[width + 2];
		double from = intervals.get(0)[0];
		double to = intervals.get(0)[1];
		for (int i = 1; i <= intervals.size(); i++) {
			if (i < intervals.size() && intervals.get(i)[0] <= to) {
				to = Math.max(to, intervals.get(i)[1]);
				continue;
			}
			diff[clamp((int) Math.floor(from), width)]++;
			diff[clamp((int) Math.ceil(to), width)]--;
			if (i < intervals.size()) {
				from = intervals.get(i)[0];
				to = intervals.get(i)[1];
			}
		}
		int running = 0;
		for (int x = 0; x <= width; x++) {
			running += diff[x];
			if (running > 0) {
				crossing[x]++;
			}
		}
	}

	private static int clamp(int value, int width) {
		return Math.max(0, Math.min(width, value));
	}

	private static boolean bothSidesHaveText(List<Glyph> glyphs, double middle) {
		long left = glyphs.stream().filter(glyph -> glyph.x() + glyph.width() / 2 < middle).count();
		double leftShare = (double) left / glyphs.size();
		return leftShare >= GUTTER_MIN_SIDE_SHARE && 1 - leftShare >= GUTTER_MIN_SIDE_SHARE;
	}
}
