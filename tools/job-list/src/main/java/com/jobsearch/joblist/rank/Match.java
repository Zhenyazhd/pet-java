package com.jobsearch.joblist.rank;

import com.jobsearch.joblist.job.Job;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** How well one job fits one profile, as judged by the model. */
@Entity
@Table(name = "match")
public class Match {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "profile_id")
	private Long profileId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "job_id")
	private Job job;

	private int score;
	private String reason;

	@Column(name = "prompt_hash")
	private String promptHash;

	@Column(name = "ranked_at")
	private Instant rankedAt;

	@Column(name = "delivered_at")
	private Instant deliveredAt;

	public static Match of(long profileId, Job job) {
		Match match = new Match();
		match.profileId = profileId;
		match.job = job;
		return match;
	}

	public void rank(int score, String reason, String promptHash) {
		this.score = score;
		this.reason = reason;
		this.promptHash = promptHash;
		this.rankedAt = Instant.now();
	}

	public void markDelivered() {
		this.deliveredAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Job getJob() {
		return job;
	}

	public int getScore() {
		return score;
	}

	public String getReason() {
		return reason;
	}

	public Instant getRankedAt() {
		return rankedAt;
	}

	public Instant getDeliveredAt() {
		return deliveredAt;
	}
}
