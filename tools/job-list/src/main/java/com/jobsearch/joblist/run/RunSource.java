package com.jobsearch.joblist.run;

import com.jobsearch.joblist.source.SourceName;
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

/** What happened to one source in one run; also the record of what was spent. */
@Entity
@Table(name = "run_source")
public class RunSource {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "run_id")
	private Long runId;

	@Enumerated(EnumType.STRING)
	private SourceName source;

	@Enumerated(EnumType.STRING)
	private SourceStatus status;

	@Column(name = "apify_run_id")
	private String apifyRunId;

	private int fetched;

	@Column(name = "new_jobs")
	private int newJobs;

	private int dropped;

	@Column(name = "cost_usd")
	private BigDecimal costUsd = BigDecimal.ZERO;

	@Column(name = "started_at")
	private Instant startedAt;

	private String error;

	public static RunSource of(long runId, SourceName source, SourceStatus status, Instant startedAt) {
		RunSource row = new RunSource();
		row.runId = runId;
		row.source = source;
		row.status = status;
		row.startedAt = startedAt;
		return row;
	}

	public RunSource withStatus(SourceStatus status) {
		this.status = status;
		return this;
	}

	public RunSource withResult(String apifyRunId, int fetched, int newJobs, int dropped, BigDecimal costUsd, String error) {
		this.apifyRunId = apifyRunId;
		this.fetched = fetched;
		this.newJobs = newJobs;
		this.dropped = dropped;
		this.costUsd = costUsd;
		this.error = error;
		return this;
	}

	public Long getRunId() {
		return runId;
	}

	public SourceName getSource() {
		return source;
	}

	public SourceStatus getStatus() {
		return status;
	}

	public String getApifyRunId() {
		return apifyRunId;
	}

	public int getFetched() {
		return fetched;
	}

	public int getNewJobs() {
		return newJobs;
	}

	public int getDropped() {
		return dropped;
	}

	public BigDecimal getCostUsd() {
		return costUsd;
	}

	public String getError() {
		return error;
	}
}
