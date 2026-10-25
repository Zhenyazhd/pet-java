package com.jobsearch.core_api.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The text an admin made the default of one prompt part for everyone; see {@link UserPromptService#publish()}. */
@Entity
@Table(name = "prompt_default")
public class PromptDefault {

	@Id
	@Column(name = "part_key", length = 40)
	private String partKey;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	protected PromptDefault() {
	}

	public String getPartKey() {
		return partKey;
	}

	public String getContent() {
		return content;
	}
}
