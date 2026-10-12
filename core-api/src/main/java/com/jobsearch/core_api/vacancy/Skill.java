package com.jobsearch.core_api.vacancy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** A skill or requirement seen in any vacancy. Rows are only ever added, by {@link SkillService}. */
@Entity
@Immutable
@Table(name = "skill")
public class Skill {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(name = "name_key", nullable = false, columnDefinition = "TEXT")
	private String nameKey;

	protected Skill() {
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	String getNameKey() {
		return nameKey;
	}
}
