package com.jobsearch.core_api.resume;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Size caps keep a resume within what the Typst renderer/LLM prompts can reasonably handle
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
			@NotNull @Size(max = 50) List<@NotNull @Valid ExperienceItem> experience,
			@NotNull @Size(max = 30) List<@NotNull @Valid EducationItem> education,
			@NotNull @Size(max = 50) List<@NotNull @Valid AchievementItem> achievements,
			@NotNull @Size(max = 50) List<@NotNull @Valid SkillItem> skills,
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
			Integer version,
			/** How the PDF is typeset; null or a missing field means the template's own default. */
			@Valid LayoutSettings layout
	) {

		/** A resume without layout settings, which is what everything but the layout editor works with. */
		public ResumeDocument(String name, String headline, String phone, String email, String linkedinUrl,
				String linkedinLabel, String profile, List<ExperienceItem> experience, List<EducationItem> education,
				List<AchievementItem> achievements, List<SkillItem> skills, String locale, Integer version) {
			this(name, headline, phone, email, linkedinUrl, linkedinLabel, profile, experience, education, achievements,
					skills, locale, version, null);
		}
	}

	/**
	 * Typesetting knobs, each optional (a missing one keeps the template's value). The ranges keep the page legible and
	 * the compile safe; the editor's inputs use the same ones (frontend/src/lib/resume/layout.ts).
	 */
	public record LayoutSettings(
			/** Body text size, pt. */
			@DecimalMin("8") @DecimalMax("14") Double fontSize,
			/** Extra space between lines of a paragraph, em. */
			@DecimalMin("0.2") @DecimalMax("1.5") Double lineSpacing,
			/** Space between paragraphs, em. */
			@DecimalMin("0") @DecimalMax("2") Double paragraphSpacing,
			/** Space above a section heading, em. */
			@DecimalMin("0") @DecimalMax("3") Double sectionSpacing,
			/** Space between bullets, em. */
			@DecimalMin("0") @DecimalMax("1.5") Double bulletSpacing,
			/** Space between a title (job, school) and the line under it, em. */
			@DecimalMin("0") @DecimalMax("1.5") Double subtitleSpacing,
			/** Space above the first bullet of a job (and of the achievements), em. */
			@DecimalMin("0") @DecimalMax("2") Double bulletsTopSpacing,
			/** Left and right page margin, cm. */
			@DecimalMin("0.5") @DecimalMax("4") Double marginX,
			@DecimalMin("0.3") @DecimalMax("4") Double marginTop,
			@DecimalMin("0.3") @DecimalMax("4") Double marginBottom,
			/** True (or missing): the pdf worker scales the type slightly so the text fills exactly one page. */
			Boolean autoFit
	) {
	}

	public record ExperienceItem(
			@NotBlank @Size(max = 200) String title,
			@NotNull @Size(max = 300) String subtitle,
			@NotNull @Size(max = 100) String dates,
			@NotNull @Size(max = 50) List<@NotNull @Size(max = 1000) String> bullets
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
}
