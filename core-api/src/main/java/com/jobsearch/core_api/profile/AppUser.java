package com.jobsearch.core_api.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "app_user")
public class AppUser {

	@Id
	private Long id;

	@Column(nullable = false, length = 255)
	private String email;

	@Column(name = "display_name", nullable = false, length = 255)
	private String displayName;

	@Column(name = "career_path", nullable = false, columnDefinition = "TEXT")
	private String careerPath = "";

	@Column(name = "resume_json", nullable = false, columnDefinition = "TEXT")
	private String resumeJson = "";

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getCareerPath() {
		return careerPath;
	}

	public void setCareerPath(String careerPath) {
		this.careerPath = careerPath;
	}

	public String getResumeJson() {
		return resumeJson;
	}

	public void setResumeJson(String resumeJson) {
		this.resumeJson = resumeJson;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
