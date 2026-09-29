package com.jobsearch.core_api.resume;

import com.jobsearch.core_api.resume.ResumeDtos.AchievementItem;
import com.jobsearch.core_api.resume.ResumeDtos.EducationItem;
import com.jobsearch.core_api.resume.ResumeDtos.ExperienceItem;
import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import com.jobsearch.core_api.resume.ResumeDtos.SkillItem;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Turns ResumeDocument JSON into the project LaTeX CV template. */
@Component
public class ResumeLatexRenderer {

	public String render(ResumeDocument r) {
		SectionTitles titles = SectionTitles.forLocale(r.locale());
		StringBuilder sb = new StringBuilder(8_000);
		sb.append("""
				\\documentclass[10pt]{extarticle}

				\\usepackage[margin=1.2cm, top=0.8cm, bottom=0.75cm]{geometry}
				\\usepackage[T1]{fontenc}
				\\usepackage[utf8]{inputenc}
				\\usepackage{mathpazo}
				\\usepackage[fontsize=9.7pt]{fontsize}
				\\usepackage{titlesec}
				\\usepackage{enumitem}
				\\usepackage{xcolor}
				\\usepackage{hyperref}
				\\usepackage{microtype}
				\\emergencystretch=2em
				\\sloppy

				\\definecolor{accent}{HTML}{1F3B57}
				\\definecolor{soft}{HTML}{5A5A5A}
				\\definecolor{rule}{HTML}{1F3B57}

				\\hypersetup{colorlinks=true, urlcolor=accent, linkcolor=accent}

				\\setlength{\\parindent}{0pt}
				\\pagestyle{empty}
				\\setlength{\\tabcolsep}{0pt}
				\\linespread{0.96}

				\\titleformat{\\section}
				  {\\bfseries\\color{accent}\\fontsize{11.5}{11}\\selectfont}
				  {}{0em}{}
				  [{\\color{rule}\\titlerule[0.9pt]}]
				\\titlespacing*{\\section}{0pt}{6pt}{3pt}

				\\newcommand{\\role}[3]{%
				  \\textbf{#1} \\hfill {\\color{soft}\\small #3}\\\\[-1pt]
				  {\\itshape\\small #2}\\par
				}
				\\newcommand{\\edu}[3]{%
				  \\textbf{#1} \\hfill {\\color{soft}\\small #3}\\\\[-1pt]
				  {\\itshape\\small #2}\\par
				}

				\\newlist{tightlist}{itemize}{1}
				\\setlist[tightlist]{leftmargin=12pt, itemsep=2pt, topsep=2.5pt, parsep=0pt, label=\\textbullet, font=\\color{soft}}
				\\setlength{\\parskip}{0pt}

				\\begin{document}

				""");

		sb.append("\\begin{center}\n");
		sb.append("    {\\fontsize{18}{18}\\selectfont\\bfseries\\color{accent} ")
				.append(esc(r.name())).append("}\\\\[3pt]\n");
		sb.append("    {\\normalsize\\color{soft} ").append(esc(r.headline())).append("}\\\\[4pt]\n");
		sb.append("    {\\footnotesize\n");
		sb.append("      ").append(phoneLatex(r.phone())).append(" \\quad|\\quad\n");
		sb.append("      \\href{mailto:").append(escUrl(r.email())).append("}{")
				.append(esc(r.email())).append("} \\quad|\\quad\n");
		sb.append("      \\href{").append(escUrl(r.linkedinUrl())).append("}{")
				.append(esc(r.linkedinLabel())).append("}\n");
		sb.append("    }\n\\end{center}\n\\vspace{-9pt}\n\n");

		sb.append("\\section{").append(esc(titles.profile())).append("}\n\n")
				.append(esc(r.profile())).append("\n\n");

		sb.append("\\section{").append(esc(titles.experience())).append("}\n\n");
		for (ExperienceItem job : nullToEmpty(r.experience())) {
			sb.append("\\role{").append(esc(job.title())).append("}{")
					.append(esc(job.subtitle())).append("}{")
					.append(esc(job.dates())).append("}\n\n");
			List<String> bullets = nonBlank(job.bullets());
			if (!bullets.isEmpty()) {
				sb.append("\\begin{tightlist}\n");
				for (String bullet : bullets) {
					sb.append("    \\item ").append(esc(bullet)).append("\n\n");
				}
				sb.append("\\end{tightlist}\n\n");
			}
		}

		sb.append("\\section{").append(esc(titles.education())).append("}\n\n");
		for (EducationItem edu : nullToEmpty(r.education())) {
			sb.append("\\edu{").append(esc(edu.title())).append("}{")
					.append(esc(edu.subtitle())).append("}{")
					.append(esc(edu.location())).append("}\n\n");
			if (edu.details() != null && !edu.details().isBlank()) {
				sb.append("{\\footnotesize ").append(esc(edu.details())).append("}\n\n");
			}
		}

		List<AchievementItem> achievements = nullToEmpty(r.achievements()).stream()
				.filter(a -> (a.title() != null && !a.title().isBlank())
						|| (a.text() != null && !a.text().isBlank()))
				.toList();
		if (!achievements.isEmpty()) {
			sb.append("\\section{").append(esc(titles.achievements())).append("}\n\n\\begin{tightlist}\n");
			for (AchievementItem a : achievements) {
				sb.append("\\item \\textbf{").append(esc(a.title())).append(" :} ")
						.append(esc(a.text())).append("\n");
			}
			sb.append("\\end{tightlist}\n\n");
		}

		List<SkillItem> skills = nullToEmpty(r.skills()).stream()
				.filter(s -> (s.category() != null && !s.category().isBlank())
						|| (s.items() != null && !s.items().isBlank()))
				.toList();
		if (!skills.isEmpty()) {
			sb.append("\\section{").append(esc(titles.skills())).append("}\n\n\\begin{tightlist}\n");
			for (SkillItem s : skills) {
				sb.append("    \\item[] \\textbf{").append(esc(s.category())).append(" :} ")
						.append(esc(s.items())).append("\n\n");
			}
			sb.append("\\end{tightlist}\n");
		}
		sb.append("\\end{document}\n");
		return sb.toString();
	}

	private record SectionTitles(
			String profile,
			String experience,
			String education,
			String achievements,
			String skills
	) {
		static SectionTitles forLocale(String locale) {
			if (locale != null && locale.strip().equalsIgnoreCase("en")) {
				return new SectionTitles(
						"Profile",
						"Professional Experience",
						"Education",
						"Achievements",
						"Skills"
				);
			}
			return new SectionTitles(
					"Profil",
					"Expérience professionnelle",
					"Education",
					"Réalisations",
					"Compétences"
			);
		}
	}

	private static String phoneLatex(String phone) {
		return esc(phone == null ? "" : phone).replace(" ", "~");
	}

	static String esc(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder(value.length() + 16);
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '\\' -> out.append("\\textbackslash{}");
				case '{' -> out.append("\\{");
				case '}' -> out.append("\\}");
				case '#' -> out.append("\\#");
				case '$' -> out.append("\\$");
				case '%' -> out.append("\\%");
				case '&' -> out.append("\\&");
				case '_' -> out.append("\\_");
				case '~' -> out.append("\\textasciitilde{}");
				case '^' -> out.append("\\textasciicircum{}");
				default -> out.append(c);
			}
		}
		return out.toString();
	}

	private static String escUrl(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "").replace("{", "").replace("}", "").replace("#", "");
	}

	private static <T> List<T> nullToEmpty(List<T> values) {
		return values == null ? List.of() : values;
	}

	private static List<String> nonBlank(List<String> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		List<String> out = new ArrayList<>(values.size());
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				out.add(value);
			}
		}
		return out;
	}
}
