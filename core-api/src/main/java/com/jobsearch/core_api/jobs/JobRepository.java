package com.jobsearch.core_api.jobs;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every status change after insert happens here as a single UPDATE. The ones that finish a job
 * match on {@code status = 'RUNNING' AND attempts = :attempt}, so an outcome that arrives after its
 * lease was taken back updates nothing and is discarded.
 */
public interface JobRepository extends JpaRepository<Job, UUID> {

	Optional<Job> findByIdAndUserIdAndType(UUID id, Long userId, JobType type);

	Optional<Job> findFirstByUserIdAndTypeAndDedupeKeyAndStatusIn(
			Long userId,
			JobType type,
			String dedupeKey,
			Collection<JobStatus> statuses
	);

	/** Takes the oldest queued job of one type; {@code SKIP LOCKED} lets several instances share the queue. */
	@Transactional
	@Query(value = """
			UPDATE background_job
			SET status = 'RUNNING', attempts = attempts + 1, started_at = NOW(),
			    lease_until = NOW() + make_interval(secs => :leaseSeconds)
			WHERE id = (
			    SELECT id FROM background_job
			    WHERE status = 'QUEUED' AND type = :type
			    ORDER BY created_at
			    FOR UPDATE SKIP LOCKED
			    LIMIT 1
			)
			RETURNING *
			""", nativeQuery = true)
	Optional<Job> claimNext(@Param("type") String type, @Param("leaseSeconds") long leaseSeconds);

	@Transactional
	@Modifying
	@Query(value = """
			UPDATE background_job
			SET status = 'DONE', payload = NULL, result = :result, lease_until = NULL, finished_at = NOW()
			WHERE id = :id AND status = 'RUNNING' AND attempts = :attempt
			""", nativeQuery = true)
	int markDone(@Param("id") UUID id, @Param("attempt") int attempt, @Param("result") String result);

	@Transactional
	@Modifying
	@Query(value = """
			UPDATE background_job
			SET status = 'FAILED', payload = NULL, lease_until = NULL, finished_at = NOW(),
			    error_code = :code, error_message = :message
			WHERE id = :id AND status = 'RUNNING' AND attempts = :attempt
			""", nativeQuery = true)
	int markFailed(
			@Param("id") UUID id,
			@Param("attempt") int attempt,
			@Param("code") String code,
			@Param("message") String message
	);

	@Transactional
	@Modifying
	@Query(value = """
			UPDATE background_job
			SET status = 'QUEUED', started_at = NULL, lease_until = NULL
			WHERE id = :id AND status = 'RUNNING' AND attempts = :attempt
			""", nativeQuery = true)
	int requeue(@Param("id") UUID id, @Param("attempt") int attempt);

	/** For a busy downstream service: the job goes back to the queue and this try is not counted. */
	@Transactional
	@Modifying
	@Query(value = """
			UPDATE background_job
			SET status = 'QUEUED', attempts = attempts - 1, started_at = NULL, lease_until = NULL
			WHERE id = :id AND status = 'RUNNING' AND attempts = :attempt
			""", nativeQuery = true)
	int requeueWithoutCountingAttempt(@Param("id") UUID id, @Param("attempt") int attempt);

	@Transactional
	@Modifying
	@Query(value = """
			UPDATE background_job
			SET status = 'QUEUED', started_at = NULL, lease_until = NULL
			WHERE type = :type AND status = 'RUNNING' AND lease_until < NOW() AND attempts < :maxAttempts
			""", nativeQuery = true)
	int requeueExpiredLeases(@Param("type") String type, @Param("maxAttempts") int maxAttempts);

	@Transactional
	@Modifying
	@Query(value = """
			UPDATE background_job
			SET status = 'FAILED', payload = NULL, lease_until = NULL, finished_at = NOW(),
			    error_code = :code, error_message = :message
			WHERE type = :type AND status = 'RUNNING' AND lease_until < NOW() AND attempts >= :maxAttempts
			""", nativeQuery = true)
	int failExpiredLeases(
			@Param("type") String type,
			@Param("maxAttempts") int maxAttempts,
			@Param("code") String code,
			@Param("message") String message
	);

	@Transactional
	@Modifying
	@Query(value = """
			UPDATE background_job
			SET status = 'FAILED', payload = NULL, finished_at = NOW(), error_code = :code, error_message = :message
			WHERE status = 'QUEUED' AND created_at < :cutoff
			""", nativeQuery = true)
	int failQueuedBefore(
			@Param("cutoff") Instant cutoff,
			@Param("code") String code,
			@Param("message") String message
	);

	@Transactional
	@Modifying
	@Query(value = """
			DELETE FROM background_job WHERE id IN (
			    SELECT id FROM background_job
			    WHERE status IN ('DONE', 'FAILED') AND finished_at < :cutoff
			    LIMIT :batchSize
			)
			""", nativeQuery = true)
	int deleteFinishedBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
