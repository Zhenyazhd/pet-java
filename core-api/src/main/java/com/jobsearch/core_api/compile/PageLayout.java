package com.jobsearch.core_api.compile;

/**
 * How a compiled resume sits on its pages, as measured by the pdf worker: the page count ({@code X-Page-Count}), how
 * full the last page is, 0..1 ({@code X-Page-Fill}), and the layout scale the worker's fit search settled on
 * ({@code X-Page-Scale}; 1.0 is the nominal typography, below it the layout was tightened, above it stretched).
 * {@code scale} is null when the worker did not report one.
 */
public record PageLayout(int pages, double fill, Double scale) {

	public PageLayout(int pages, double fill) {
		this(pages, fill, null);
	}
}
