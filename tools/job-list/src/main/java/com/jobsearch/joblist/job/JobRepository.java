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

	List<Job> findByFetchedAtAfterOrderByIdDesc(Instant since);

	@Transactional
	@Modifying
	@Query("delete from Job j where j.fetchedAt < :before")
	int deleteFetchedBefore(@Param("before") Instant before);
}
