package com.jobsearch.core_api.vacancy;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "vacancy")
public class Vacancy {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String url;

	@Column(nullable = false, length = 255)
	private String title;

	@Column(length = 255)
	private String company;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "match_percent")
	@JdbcTypeCode(SqlTypes.SMALLINT)
	private Integer matchPercent;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@OneToMany(mappedBy = "vacancy", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<VacancyRequirement> requirements = new ArrayList<>();

	@Enumerated(EnumType.STRING)
	@Column(name = "application_status", nullable = false, length = 32)
	private ApplicationStatus applicationStatus = ApplicationStatus.NOT_APPLIED;

	@Column(name = "applied_at")
	private Instant appliedAt;

	@Column(columnDefinition = "TEXT")
	private String notes;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public void addRequirement(Skill skill, boolean required) {
		VacancyRequirement requirement = new VacancyRequirement();
		requirement.setSkill(skill);
		requirement.setRequired(required);
		requirement.setVacancy(this);
		requirements.add(requirement);
	}

	void syncRequirements(List<SkillService.Resolved> items) {
		Map<Long, VacancyRequirement> existing = new HashMap<>();
		for (VacancyRequirement requirement : requirements) {
			existing.put(requirement.getSkill().getId(), requirement);
		}
		Set<Long> keep = new HashSet<>();
		for (SkillService.Resolved item : items) {
			Long skillId = item.skill().getId();
			keep.add(skillId);
			VacancyRequirement match = existing.get(skillId);
			if (match == null) {
				addRequirement(item.skill(), item.required());
			}
			else {
				match.setRequired(item.required());
			}
		}
		requirements.removeIf(requirement -> {
			if (keep.contains(requirement.getSkill().getId())) {
				return false;
			}
			requirement.setVacancy(null);
			return true;
		});
	}

	public Long getId() {
		return id;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getCompany() {
		return company;
	}

	public void setCompany(String company) {
		this.company = company;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Integer getMatchPercent() {
		return matchPercent;
	}

	public void setMatchPercent(Integer matchPercent) {
		this.matchPercent = matchPercent;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public List<VacancyRequirement> getRequirements() {
		return requirements;
	}

	public ApplicationStatus getApplicationStatus() {
		return applicationStatus;
	}

	/** The date of the first application is kept through later statuses and cleared when back to NOT_APPLIED. */
	public void setApplicationStatus(ApplicationStatus status) {
		this.applicationStatus = status;
		if (status == ApplicationStatus.NOT_APPLIED) {
			appliedAt = null;
		}
		else if (appliedAt == null) {
			appliedAt = Instant.now();
		}
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}
}
