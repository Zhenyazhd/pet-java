package com.jobsearch.joblist.run;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** One execution of a search profile: fetch, store, rank. */
@Entity
@Table(name = "run")
public class Run {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "profile_id")
	private Long profileId;

	@Enumerated(EnumType.STRING)
	private RunStatus status;

	@Enumerated(EnumType.STRING)
	private RunMode mode = RunMode.FULL;

	@Column(name = "created_at")
	private Instant createdAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Column(name = "lease_until")
	private Instant leaseUntil;

	private int fetched;

	@Column(name = "new_jobs")
	private int newJobs;

	private int ranked;

	@Column(name = "cost_usd")
	private BigDecimal costUsd = BigDecimal.ZERO;

	private String error;

	public static Run queued(long profileId) {
		return queued(profileId, RunMode.FULL);
	}

	public static Run queued(long profileId, RunMode mode) {
		Run run = new Run();
		run.mode = mode;
		run.profileId = profileId;
		run.status = RunStatus.QUEUED;
		run.createdAt = Instant.now();
		return run;
	}

	public void start(Instant leaseUntil) {
		this.status = RunStatus.RUNNING;
		this.startedAt = Instant.now();
		this.leaseUntil = leaseUntil;
	}

	public void finish(RunStatus status, String error) {
		this.status = status;
		this.error = error;
		this.finishedAt = Instant.now();
		this.leaseUntil = null;
	}

	public void addFetched(int fetched, int newJobs, BigDecimal costUsd) {
		this.fetched += fetched;
		this.newJobs += newJobs;
		this.costUsd = this.costUsd.add(costUsd);
	}

	public void addRanked(int ranked, BigDecimal costUsd) {
		this.ranked += ranked;
		this.costUsd = this.costUsd.add(costUsd);
	}

	public Long getId() {
		return id;
	}

	public Long getProfileId() {
		return profileId;
	}

	public RunMode getMode() {
		return mode;
	}

	public RunStatus getStatus() {
		return status;
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

	public int getFetched() {
		return fetched;
	}

	public int getNewJobs() {
		return newJobs;
	}

	public int getRanked() {
		return ranked;
	}

	public BigDecimal getCostUsd() {
		return costUsd;
	}

	public String getError() {
		return error;
	}
}
