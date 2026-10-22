package com.jobsearch.core_api.resume;

import com.jobsearch.core_api.resume.ResumeDtos.AchievementItem;
import com.jobsearch.core_api.resume.ResumeDtos.EducationItem;
import com.jobsearch.core_api.resume.ResumeDtos.ExperienceItem;
import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import com.jobsearch.core_api.resume.ResumeDtos.SkillItem;
import org.springframework.stereotype.Component;

/**
 * Flattens structured resume JSON into the plain text the PDF is expected to contain, for the ats-worker's
 * readability check. The headings match {@code typst/resume.typ}, and a role is written as the template typesets it
 * (title and dates on one line, the company on the next), because the check compares the order of the words.
 */
@Component
public class ResumePlainTextRenderer {

	public String render(ResumeDocument r) {
		Titles titles = Titles.forLocale(r.locale());
		StringBuilder sb = new StringBuilder(4096);
		line(sb, r.name());
		line(sb, r.headline());
		contact(sb, r);
		section(sb, titles.profile(), r.profile());

		if (r.experience() != null && !r.experience().isEmpty()) {
			sb.append(titles.experience()).append('\n');
			for (ExperienceItem item : r.experience()) {
				// as typeset: the title and the dates on one line, the company under them
				line(sb, joinNonBlank(" — ", item.title(), item.dates()));
				line(sb, item.subtitle());
				if (item.bullets() != null) {
					for (String bullet : item.bullets()) {
						if (bullet != null && !bullet.isBlank()) {
							sb.append("- ").append(bullet.strip()).append('\n');
						}
					}
				}
			}
			sb.append('\n');
		}

		if (r.education() != null && !r.education().isEmpty()) {
			sb.append(titles.education()).append('\n');
			for (EducationItem item : r.education()) {
				line(sb, joinNonBlank(" — ", item.title(), item.location()));
				line(sb, item.subtitle());
				line(sb, item.details());
			}
			sb.append('\n');
		}

		if (r.achievements() != null && !r.achievements().isEmpty()) {
			sb.append(titles.achievements()).append('\n');
			for (AchievementItem item : r.achievements()) {
				line(sb, joinNonBlank(": ", item.title(), item.text()));
			}
			sb.append('\n');
		}

		if (r.skills() != null && !r.skills().isEmpty()) {
			sb.append(titles.skills()).append('\n');
			for (SkillItem item : r.skills()) {
				line(sb, joinNonBlank(": ", item.category(), item.items()));
			}
		}

		String text = sb.toString().strip();
		if (text.length() > 50_000) {
			return text.substring(0, 50_000);
		}
		return text;
	}

	private static void contact(StringBuilder sb, ResumeDocument r) {
		line(sb, joinNonBlank(" | ", r.phone(), r.email(),
				blank(r.linkedinLabel()) ? r.linkedinUrl() : r.linkedinLabel()));
	}

	private static void section(StringBuilder sb, String title, String body) {
		if (body == null || body.isBlank()) {
			return;
		}
		sb.append(title).append('\n').append(body.strip()).append("\n\n");
	}

	private static void line(StringBuilder sb, String value) {
		if (value == null || value.isBlank()) {
			return;
		}
		sb.append(value.strip()).append('\n');
	}

	private static String joinNonBlank(String sep, String... parts) {
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part == null || part.isBlank()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(sep);
			}
			out.append(part.strip());
		}
		return out.toString();
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}

	/** The section headings of typst/resume.typ. */
	private record Titles(
			String profile,
			String experience,
			String education,
			String achievements,
			String skills
	) {
		static Titles forLocale(String locale) {
			if (locale != null && locale.strip().equalsIgnoreCase("en")) {
				return new Titles(
						"Profile",
						"Professional Experience",
						"Education",
						"Achievements",
						"Skills"
				);
			}
			return new Titles(
					"Profil",
					"Expérience professionnelle",
					"Formation",
					"Réalisations",
					"Compétences"
			);
		}
	}
}
