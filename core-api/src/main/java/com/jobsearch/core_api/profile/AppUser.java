package com.jobsearch.core_api.profile;

import com.jobsearch.core_api.auth.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "app_user")
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 255)
	private String email;

	@Column(name = "display_name", nullable = false, length = 255)
	private String displayName;

	@Column(name = "password_hash", length = 255)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private UserRole role = UserRole.USER;

	@Column(name = "career_path", nullable = false, columnDefinition = "TEXT")
	private String careerPath = "";

	@Column(name = "resume_json", nullable = false, columnDefinition = "TEXT")
	private String resumeJson = "";

	/** Bumped on every resume save; used for optimistic-concurrency rejection of stale saves. */
	@Column(name = "resume_version", nullable = false)
	private int resumeVersion = 0;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public Long getId() {
		return id;
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

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public UserRole getRole() {
		return role;
	}

	public void setRole(UserRole role) {
		this.role = role;
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

	public int getResumeVersion() {
		return resumeVersion;
	}

	public void setResumeVersion(int resumeVersion) {
		this.resumeVersion = resumeVersion;
	}

}
