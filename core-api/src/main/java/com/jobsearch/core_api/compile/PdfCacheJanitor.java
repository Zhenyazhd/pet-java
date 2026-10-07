package com.jobsearch.core_api.compile;

import java.time.Duration;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes cached PDFs nobody has asked for in a while; a later request simply compiles again. */
@Component
public class PdfCacheJanitor {

	private static final Duration UNUSED_RETENTION = Duration.ofDays(30);
	private static final int DELETE_BATCH_SIZE = 500;

	private final ResumePdfCacheRepository cacheRepository;

	public PdfCacheJanitor(ResumePdfCacheRepository cacheRepository) {
		this.cacheRepository = cacheRepository;
	}

	@Scheduled(fixedDelay = 3_600_000)
	void deleteUnused() {
		Instant cutoff = Instant.now().minus(UNUSED_RETENTION);
		int deleted;
		do {
			deleted = cacheRepository.deleteUnusedBefore(cutoff, DELETE_BATCH_SIZE);
		} while (deleted == DELETE_BATCH_SIZE);
	}
}
