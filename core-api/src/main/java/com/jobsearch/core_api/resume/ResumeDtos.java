package com.jobsearch.core_api.resume;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class ResumeDtos {

	private ResumeDtos() {
	}

	public record ResumeDocument(
			@NotBlank String name,
			@NotNull String headline,
			@NotNull String phone,
			@NotNull String email,
			@NotNull String linkedinUrl,
			@NotNull String linkedinLabel,
			@NotNull String profile,
			@NotNull List<@Valid ExperienceItem> experience,
			@NotNull List<@Valid EducationItem> education,
			@NotNull List<@Valid AchievementItem> achievements,
			@NotNull List<@Valid SkillItem> skills,
			/** Section heading language: "fr" or "en". Null/blank → fr. */
			String locale
	) {
	}

	public record ExperienceItem(
			@NotBlank String title,
			@NotNull String subtitle,
			@NotNull String dates,
			@NotNull List<String> bullets
	) {
	}

	public record EducationItem(
			@NotBlank String title,
			@NotNull String subtitle,
			@NotNull String location,
			@NotNull String details
	) {
	}

	public record AchievementItem(
			@NotBlank String title,
			@NotNull String text
	) {
	}

	public record SkillItem(
			@NotBlank String category,
			@NotNull String items
	) {
	}

	public record LatexResponse(String source) {
	}
}
