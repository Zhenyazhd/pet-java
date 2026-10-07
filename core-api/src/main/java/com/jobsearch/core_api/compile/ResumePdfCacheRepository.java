package com.jobsearch.core_api.compile;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ResumePdfCacheRepository extends JpaRepository<ResumePdfCache, String> {

	/** Marks a cached PDF as used; returns 0 when there is no PDF for this hash. */
	@Transactional
	@Modifying
	@Query(value = "UPDATE resume_pdf_cache SET last_used_at = NOW() WHERE source_hash = :hash", nativeQuery = true)
	int touch(@Param("hash") String hash);

	@Transactional
	@Modifying
	@Query(value = """
			INSERT INTO resume_pdf_cache (source_hash, pdf, size_bytes)
			VALUES (:hash, :pdf, octet_length(:pdf))
			ON CONFLICT (source_hash) DO UPDATE SET last_used_at = NOW()
			""", nativeQuery = true)
	void upsert(@Param("hash") String hash, @Param("pdf") byte[] pdf);

	@Transactional
	@Modifying
	@Query(value = """
			DELETE FROM resume_pdf_cache WHERE source_hash IN (
			    SELECT source_hash FROM resume_pdf_cache WHERE last_used_at < :cutoff LIMIT :batchSize
			)
			""", nativeQuery = true)
	int deleteUnusedBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
