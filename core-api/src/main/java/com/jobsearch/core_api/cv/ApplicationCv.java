package com.jobsearch.core_api.cv;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "application_cv")
public class ApplicationCv {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "vacancy_id", nullable = false)
	private Long vacancyId;

	@Column(name = "job_application_id")
	private Long jobApplicationId;

	@Column(name = "cv_version_id", nullable = false)
	private Long cvVersionId;

	@Column(length = 255)
	private String company;

	@Column(columnDefinition = "TEXT")
	private String notes;

	@Column(name = "sent_at", nullable = false)
	private Instant sentAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		if (sentAt == null) {
			sentAt = now;
		}
		createdAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public Long getVacancyId() {
		return vacancyId;
	}

	public void setVacancyId(Long vacancyId) {
		this.vacancyId = vacancyId;
	}

	public Long getJobApplicationId() {
		return jobApplicationId;
	}

	public void setJobApplicationId(Long jobApplicationId) {
		this.jobApplicationId = jobApplicationId;
	}

	public Long getCvVersionId() {
		return cvVersionId;
	}

	public void setCvVersionId(Long cvVersionId) {
		this.cvVersionId = cvVersionId;
	}

	public String getCompany() {
		return company;
	}

	public void setCompany(String company) {
		this.company = company;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	public void setSentAt(Instant sentAt) {
		this.sentAt = sentAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
