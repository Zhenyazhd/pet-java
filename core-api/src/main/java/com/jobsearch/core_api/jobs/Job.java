package com.jobsearch.core_api.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One piece of background work a user asked for. Status changes after creation are made by
 * {@link JobRepository} update queries, so this entity only needs getters.
 */
@Entity
@Table(name = "background_job")
public class Job {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private JobType type;

	/** Hash of the inputs; with user and type it identifies "the same request". */
	@Column(name = "dedupe_key", nullable = false, length = 64)
	private String dedupeKey;

	/** Input for the handler; cleared once the job finishes. */
	@Column(columnDefinition = "TEXT")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private JobStatus status;

	@Column(nullable = false)
	private int attempts;

	@Column(columnDefinition = "TEXT")
	private String result;

	@Column(name = "error_code", length = 32)
	private String errorCode;

	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	protected Job() {
	}

	static Job queued(JobType type, Long userId, String dedupeKey, String payload) {
		Job job = new Job();
		job.id = UUID.randomUUID();
		job.userId = userId;
		job.type = type;
		job.dedupeKey = dedupeKey;
		job.payload = payload;
		job.status = JobStatus.QUEUED;
		job.createdAt = Instant.now();
		return job;
	}

	/** A request that is already satisfied (e.g. its PDF is cached): nothing to run. */
	static Job done(JobType type, Long userId, String dedupeKey) {
		Job job = queued(type, userId, dedupeKey, null);
		job.status = JobStatus.DONE;
		job.finishedAt = job.createdAt;
		return job;
	}

	public UUID getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public JobType getType() {
		return type;
	}

	public String getDedupeKey() {
		return dedupeKey;
	}

	public String getPayload() {
		return payload;
	}

	public JobStatus getStatus() {
		return status;
	}

	public int getAttempts() {
		return attempts;
	}

	public String getResult() {
		return result;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}
}
