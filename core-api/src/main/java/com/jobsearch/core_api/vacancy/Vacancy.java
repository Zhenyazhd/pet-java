package com.jobsearch.core_api.vacancy;

import com.jobsearch.core_api.jobapplication.JobApplication;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

	@OneToOne(mappedBy = "vacancy", cascade = CascadeType.ALL, orphanRemoval = true)
	private JobApplication application;

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

	public void addRequirement(String name, boolean required) {
		VacancyRequirement requirement = new VacancyRequirement();
		requirement.setName(name);
		requirement.setRequired(required);
		requirement.setVacancy(this);
		requirements.add(requirement);
	}

	void syncRequirements(List<VacancyRequirements.Item> items) {
		Map<String, VacancyRequirement> existing = new HashMap<>();
		for (VacancyRequirement requirement : requirements) {
			existing.put(requirement.getName().toLowerCase(Locale.ROOT), requirement);
		}
		Set<String> keep = new HashSet<>();
		for (VacancyRequirements.Item item : items) {
			String key = item.name().toLowerCase(Locale.ROOT);
			keep.add(key);
			VacancyRequirement match = existing.get(key);
			if (match == null) {
				addRequirement(item.name(), item.required());
			}
			else {
				match.setName(item.name());
				match.setRequired(item.required());
			}
		}
		requirements.removeIf(requirement -> {
			if (keep.contains(requirement.getName().toLowerCase(Locale.ROOT))) {
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

	public JobApplication getApplication() {
		return application;
	}

	public void setApplication(JobApplication application) {
		this.application = application;
		if (application != null) {
			application.setVacancy(this);
		}
	}
}
