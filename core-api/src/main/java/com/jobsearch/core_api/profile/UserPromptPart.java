package com.jobsearch.core_api.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** A user's own text for one prompt part; the row exists only while the part is customised. */
@Entity
@Table(name = "user_prompt_part")
public class UserPromptPart {

	@Embeddable
	public record Id(
			@Column(name = "user_id") long userId,
			@Column(name = "part_key", length = 40) String partKey
	) {
	}

	@EmbeddedId
	private Id id;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	/** sha256 of the default text this content was written against. */
	@Column(name = "base_hash", nullable = false, length = 64)
	private String baseHash;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected UserPromptPart() {
	}

	public Id getId() {
		return id;
	}

	public String getContent() {
		return content;
	}

	public String getBaseHash() {
		return baseHash;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
