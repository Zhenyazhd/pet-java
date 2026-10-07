package com.jobsearch.core_api.compile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One request to turn the current resume into a PDF. Status changes after creation are made by
 * {@link CompileJobRepository} update queries, so this entity only needs getters.
 */
@Entity
@Table(name = "compile_job")
public class CompileJob {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "source_hash", nullable = false, length = 64)
	private String sourceHash;

	/** The LaTeX to compile; cleared once the job finishes. */
	@Column(columnDefinition = "TEXT")
	private String source;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private CompileJobStatus status;

	@Column(nullable = false)
	private int attempts;

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

	protected CompileJob() {
	}

	static CompileJob queued(Long userId, String sourceHash, String source) {
		CompileJob job = new CompileJob();
		job.id = UUID.randomUUID();
		job.userId = userId;
		job.sourceHash = sourceHash;
		job.source = source;
		job.status = CompileJobStatus.QUEUED;
		job.createdAt = Instant.now();
		return job;
	}

	/** A job whose PDF was already in the cache: nothing to compile. */
	static CompileJob doneFromCache(Long userId, String sourceHash) {
		CompileJob job = queued(userId, sourceHash, null);
		job.status = CompileJobStatus.DONE;
		job.finishedAt = job.createdAt;
		return job;
	}

	public UUID getId() {
		return id;
	}

	public String getSourceHash() {
		return sourceHash;
	}

	public String getSource() {
		return source;
	}

	public CompileJobStatus getStatus() {
		return status;
	}

	public int getAttempts() {
		return attempts;
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
