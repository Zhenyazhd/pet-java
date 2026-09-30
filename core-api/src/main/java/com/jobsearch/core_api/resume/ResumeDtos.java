package com.jobsearch.core_api.resume;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Size caps keep a resume within what the LaTeX renderer/LLM prompts can reasonably handle
 * and stop an authenticated user from writing unbounded TEXT into the database — none of
 * these endpoints sit behind the per-user rate limiter, so validation is the only guard.
 */
public final class ResumeDtos {

	private ResumeDtos() {
	}

	public record ResumeDocument(
			@NotBlank @Size(max = 200) String name,
			@NotNull @Size(max = 300) String headline,
			@NotNull @Size(max = 100) String phone,
			@NotNull @Size(max = 320) String email,
			@NotNull @Size(max = 500) String linkedinUrl,
			@NotNull @Size(max = 200) String linkedinLabel,
			@NotNull @Size(max = 5000) String profile,
			@NotNull @Size(max = 50) List<@Valid ExperienceItem> experience,
			@NotNull @Size(max = 30) List<@Valid EducationItem> education,
			@NotNull @Size(max = 50) List<@Valid AchievementItem> achievements,
			@NotNull @Size(max = 50) List<@Valid SkillItem> skills,
			/** Section heading language: "fr" or "en". Null/blank → fr. */
			String locale,
			/**
			 * Optimistic-concurrency token: the version this edit was based on. GET returns the
			 * current version; PUT must echo it back, and is rejected with 409 if it's stale —
			 * this is what stops a second tab/session from silently overwriting a newer save.
			 * Nullable (not a primitive int) so JSON without this field — the bundled default
			 * template, or a resume saved before this field existed — deserializes as null
			 * instead of failing; {@link ResumeService} always overrides it with the DB's
			 * authoritative value on read, and treats a null/mismatched value as stale on write.
			 */
			Integer version
	) {
	}

	public record ExperienceItem(
			@NotBlank @Size(max = 200) String title,
			@NotNull @Size(max = 300) String subtitle,
			@NotNull @Size(max = 100) String dates,
			@NotNull @Size(max = 50) List<@Size(max = 1000) String> bullets
	) {
	}

	public record EducationItem(
			@NotBlank @Size(max = 200) String title,
			@NotNull @Size(max = 300) String subtitle,
			@NotNull @Size(max = 200) String location,
			@NotNull @Size(max = 2000) String details
	) {
	}

	public record AchievementItem(
			@NotBlank @Size(max = 200) String title,
			@NotNull @Size(max = 2000) String text
	) {
	}

	public record SkillItem(
			@NotBlank @Size(max = 200) String category,
			@NotNull @Size(max = 2000) String items
	) {
	}

	public record LatexResponse(String source) {
	}
}
