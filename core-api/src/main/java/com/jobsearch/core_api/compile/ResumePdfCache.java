package com.jobsearch.core_api.compile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Compiled PDF keyed by the sha256 of its LaTeX source; written only by {@link ResumePdfCacheRepository#upsert}. */
@Entity
@Table(name = "resume_pdf_cache")
public class ResumePdfCache {

	@Id
	@Column(name = "source_hash", length = 64)
	private String sourceHash;

	@Column(nullable = false)
	private byte[] pdf;

	protected ResumePdfCache() {
	}

	public byte[] getPdf() {
		return pdf;
	}
}
