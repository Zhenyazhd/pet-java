package com.jobsearch.atsworker;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Recognises the section headings an ATS looks for; anything else ("My Journey") is not a section to it. */
final class Sections {

	static final List<String> CORE = List.of("experience", "education", "skills");

	/** Heading text, squashed (see {@link TextNorm#squash}), to the section it names. */
	private static final Map<String, String> HEADINGS = headings();

	private Sections() {
	}

	/** The recognised sections in order of first appearance. */
	static List<String> find(String text) {
		Set<String> found = new LinkedHashSet<>();
		for (String line : text.lines().toList()) {
			String stripped = line.strip();
			if (stripped.isEmpty() || stripped.length() > 45) {
				continue;
			}
			String section = HEADINGS.get(TextNorm.squash(stripped));
			if (section != null) {
				found.add(section);
			}
		}
		return new ArrayList<>(found);
	}

	private static Map<String, String> headings() {
		Map<String, String> map = new java.util.HashMap<>();
		add(map, "summary", "summary", "professional summary", "profile", "professional profile", "about me", "about",
				"objective", "career objective", "о себе", "обо мне", "профиль", "цель", "резюме", "profil", "profil professionnel", "résumé",
				"à propos");
		add(map, "experience", "experience", "work experience", "professional experience", "employment",
				"employment history", "work history", "career history", "relevant experience", "опыт работы", "опыт",
				"места работы", "expérience professionnelle", "expériences professionnelles", "expérience", "parcours professionnel");
		add(map, "education", "education", "academic background", "education and training", "academic qualifications",
				"образование", "formation", "éducation", "formation et diplômes", "diplômes");
		add(map, "skills", "skills", "technical skills", "key skills", "core competencies", "skills and tools",
				"technologies", "tech stack", "навыки", "ключевые навыки", "технические навыки", "стек", "compétences", "compétences techniques", "compétences clés", "technologies et outils");
		add(map, "projects", "projects", "personal projects", "selected projects", "проекты", "projets", "projets personnels");
		add(map, "achievements", "achievements", "key achievements", "accomplishments", "awards", "honors and awards",
				"достижения", "награды", "réalisations", "réussites", "distinctions");
		add(map, "certifications", "certifications", "certificates", "licenses and certifications", "сертификаты", "certificats", "certifications et formations");
		add(map, "languages", "languages", "языки", "langues");
		add(map, "publications", "publications", "публикации");
		add(map, "volunteering", "volunteering", "volunteer experience", "волонтерство");
		return map;
	}

	private static void add(Map<String, String> map, String section, String... names) {
		for (String name : names) {
			map.put(TextNorm.squash(name), section);
		}
	}
}
