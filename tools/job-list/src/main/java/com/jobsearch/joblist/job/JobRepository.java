package com.jobsearch.joblist.job;

import com.jobsearch.joblist.source.SourceName;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface JobRepository extends JpaRepository<Job, Long> {

	boolean existsBySourceAndExternalId(SourceName source, String externalId);

	boolean existsByFingerprint(String fingerprint);

	/** Fetched after {@code since} and not older than {@code postedAfter} by posting date (fetch date when it has none), newest first. */
	@Query("select j from Job j where j.fetchedAt > :since and coalesce(j.postedAt, j.fetchedAt) >= :postedAfter order by j.id desc")
	List<Job> findCandidates(@Param("since") Instant since, @Param("postedAfter") Instant postedAfter);

	@Transactional
	@Modifying
	@Query("delete from Job j where j.fetchedAt < :before")
	int deleteFetchedBefore(@Param("before") Instant before);
}
