package com.jobsearch.joblist.run;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RunRepository extends JpaRepository<Run, Long> {

	Optional<Run> findFirstByProfileIdAndStatusIn(Long profileId, List<RunStatus> statuses);

	List<Run> findTop20ByProfileIdOrderByIdDesc(Long profileId);

	List<Run> findByStatus(RunStatus status);

	List<Run> findByStatusAndLeaseUntilBefore(RunStatus status, Instant before);

	/** Extends the lease; 0 rows means the run is no longer RUNNING (it was released), so its worker must stop. */
	@Transactional
	@Modifying
	@Query("update Run r set r.leaseUntil = :until where r.id = :id and r.status = com.jobsearch.joblist.run.RunStatus.RUNNING")
	int renewLease(@Param("id") long id, @Param("until") Instant until);

	/** Writes the result of a run, but only while it is still RUNNING: a run the janitor released stays released. */
	@Transactional
	@Modifying
	@Query("""
			update Run r set r.status = :status, r.finishedAt = :now, r.leaseUntil = null, r.fetched = :fetched,
			  r.newJobs = :newJobs, r.ranked = :ranked, r.costUsd = :costUsd, r.error = :error
			where r.id = :id and r.status = com.jobsearch.joblist.run.RunStatus.RUNNING
			""")
	int finishIfRunning(@Param("id") long id, @Param("status") RunStatus status, @Param("now") Instant now,
			@Param("fetched") int fetched, @Param("newJobs") int newJobs, @Param("ranked") int ranked,
			@Param("costUsd") java.math.BigDecimal costUsd, @Param("error") String error);

	/** Fails a RUNNING run whose worker is gone; with {@code onlyExpired} only when its lease has run out. */
	@Transactional
	@Modifying
	@Query("""
			update Run r set r.status = com.jobsearch.joblist.run.RunStatus.FAILED, r.finishedAt = :now, r.leaseUntil = null, r.error = :error
			where r.id = :id and r.status = com.jobsearch.joblist.run.RunStatus.RUNNING
			  and (:onlyExpired = false or r.leaseUntil < :now)
			""")
	int releaseIfRunning(@Param("id") long id, @Param("now") Instant now, @Param("error") String error, @Param("onlyExpired") boolean onlyExpired);

	/** Takes the oldest queued run; other workers skip rows that are locked. Call inside a transaction. */
	@Query(value = "select * from run where status = 'QUEUED' order by id limit 1 for update skip locked", nativeQuery = true)
	Optional<Run> claimNext();

	@Transactional
	@Modifying
	@Query("delete from Run r where r.createdAt < :before")
	int deleteCreatedBefore(@Param("before") Instant before);
}
