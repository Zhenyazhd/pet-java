package com.jobsearch.core_api.compile;

import com.jobsearch.core_api.common.Hashes;
import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import com.jobsearch.core_api.resume.ResumeTypstRenderer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Page count and fill of a resume, so the AI chat knows whether to add or cut text. Results are kept per Typst
 * source (an unchanged resume is not compiled again; PDF builds feed the same cache via {@link #remember}); a miss
 * compiles on the worker with a short timeout, because a person is waiting for the chat answer.
 */
@Service
public class ResumeLayoutService {

	private static final Logger log = LoggerFactory.getLogger(ResumeLayoutService.class);

	private static final int MAX_ENTRIES = 64;
	/** After the worker fails to answer, the chat stops asking for this long instead of waiting on every message. */
	private static final Duration PAUSE_AFTER_FAILURE = Duration.ofSeconds(30);

	private final ResumeTypstRenderer renderer;
	private final PdfWorkerClient workerClient;
	private final Map<String, PageLayout> bySource = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, PageLayout> eldest) {
			return size() > MAX_ENTRIES;
		}
	};
	private volatile long pausedUntilNanos = System.nanoTime() - PAUSE_AFTER_FAILURE.toNanos();

	@Autowired
	public ResumeLayoutService(
			ResumeTypstRenderer renderer,
			@Qualifier("pdfWorkerLayoutRestClient") RestClient restClient,
			ObjectMapper objectMapper
	) {
		this(renderer, new PdfWorkerClient(restClient, objectMapper));
	}

	ResumeLayoutService(ResumeTypstRenderer renderer, PdfWorkerClient workerClient) {
		this.renderer = renderer;
		this.workerClient = workerClient;
	}

	/** Empty when the worker is busy, down or rejects the document: the chat then works without layout hints. */
	public Optional<PageLayout> of(ResumeDocument resume) {
		String source = renderer.render(resume);
		String hash = Hashes.sha256(source);
		synchronized (bySource) {
			PageLayout known = bySource.get(hash);
			if (known != null) {
				return Optional.of(known);
			}
		}
		if (System.nanoTime() - pausedUntilNanos < 0) {
			log.debug("Page layout skipped: worker was unavailable a moment ago");
			return Optional.empty();
		}
		WorkerResult result = workerClient.compile(source);
		if (result instanceof WorkerResult.Pdf(byte[] bytes, PageLayout layout) && layout != null) {
			remember(hash, layout);
			return Optional.of(layout);
		}
		// A worker that answers without page headers (an old version during a deploy) is no use either.
		if (result instanceof WorkerResult.Unavailable || result instanceof WorkerResult.Pdf) {
			pausedUntilNanos = System.nanoTime() + PAUSE_AFTER_FAILURE.toNanos();
		}
		log.debug("Page layout not available: {}", result.getClass().getSimpleName());
		return Optional.empty();
	}

	/** Stores a layout the PDF build already measured, so the next chat message does not compile the same source. */
	void remember(String sourceHash, PageLayout layout) {
		synchronized (bySource) {
			bySource.put(sourceHash, layout);
		}
	}
}
