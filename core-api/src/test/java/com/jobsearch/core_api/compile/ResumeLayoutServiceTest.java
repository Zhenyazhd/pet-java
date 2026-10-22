package com.jobsearch.core_api.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobsearch.core_api.common.Hashes;
import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import com.jobsearch.core_api.resume.ResumeTypstRenderer;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResumeLayoutServiceTest {

	private static final ResumeDocument RESUME = new ResumeDocument(
			"Ada", "Engineer", "", "ada@example.com", "", "", "", List.of(), List.of(), List.of(), List.of(), "en", 1);
	private static final PageLayout LAYOUT = new PageLayout(1, 0.9);

	private final ResumeTypstRenderer renderer = mock(ResumeTypstRenderer.class);
	private final PdfWorkerClient worker = mock(PdfWorkerClient.class);
	private ResumeLayoutService service;

	@BeforeEach
	void setUp() {
		when(renderer.render(RESUME)).thenReturn("source");
		service = new ResumeLayoutService(renderer, worker);
	}

	@Test
	void sameSourceIsCompiledOnce() {
		when(worker.compile("source")).thenReturn(new WorkerResult.Pdf(new byte[] { 1 }, LAYOUT));

		assertEquals(Optional.of(LAYOUT), service.of(RESUME));
		assertEquals(Optional.of(LAYOUT), service.of(RESUME));

		verify(worker, times(1)).compile("source");
	}

	@Test
	void layoutFromAPdfBuildIsReused() {
		service.remember(Hashes.sha256("source"), LAYOUT);

		assertEquals(Optional.of(LAYOUT), service.of(RESUME));

		verify(worker, times(0)).compile(anyString());
	}

	@Test
	void failuresGiveNothingAndAreNotCached() {
		when(worker.compile("source"))
				.thenReturn(new WorkerResult.Busy(1))
				.thenReturn(new WorkerResult.Rejected("compile_error", "x"))
				.thenReturn(new WorkerResult.Pdf(new byte[] { 1 }, LAYOUT));

		assertTrue(service.of(RESUME).isEmpty());
		assertTrue(service.of(RESUME).isEmpty());
		assertEquals(Optional.of(LAYOUT), service.of(RESUME));
	}

	@Test
	void workerWithoutPageHeadersIsLeftAloneForAWhile() {
		when(worker.compile("source")).thenReturn(new WorkerResult.Pdf(new byte[] { 1 }, null));

		assertTrue(service.of(RESUME).isEmpty());
		assertTrue(service.of(RESUME).isEmpty());

		verify(worker, times(1)).compile("source");
	}

	@Test
	void oldestLayoutsAreForgottenBeyondTheLimit() {
		when(worker.compile("source")).thenReturn(new WorkerResult.Pdf(new byte[] { 1 }, LAYOUT));
		service.remember(Hashes.sha256("source"), LAYOUT);
		for (int i = 0; i < 64; i++) {
			service.remember("other-" + i, LAYOUT);
		}

		assertEquals(Optional.of(LAYOUT), service.of(RESUME));

		verify(worker, times(1)).compile("source");
	}

	@Test
	void unavailableWorkerIsLeftAloneForAWhile() {
		when(worker.compile("source")).thenReturn(new WorkerResult.Unavailable("down"));

		assertTrue(service.of(RESUME).isEmpty());
		assertTrue(service.of(RESUME).isEmpty());

		verify(worker, times(1)).compile("source");
	}
}
