package com.jobsearch.core_api.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobsearch.core_api.resume.ResumeDtos.AchievementItem;
import com.jobsearch.core_api.resume.ResumeDtos.EducationItem;
import com.jobsearch.core_api.resume.ResumeDtos.ExperienceItem;
import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import com.jobsearch.core_api.resume.ResumeDtos.SkillItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumePlainTextRendererTest {

	private static ResumeDocument resume(String locale) {
		return new ResumeDocument("Jane Doe", "Backend Engineer", "+33 6 12 34 56 78", "jane@example.com",
				"https://linkedin.com/in/jane", "linkedin.com/in/jane", "Six years of payment services.",
				List.of(new ExperienceItem("Senior Engineer", "Acme Corp, Paris", "2020 – 2024", List.of("Built a gateway"))),
				List.of(new EducationItem("BSc Computer Science", "TU Munich", "Munich", "With distinction")),
				List.of(new AchievementItem("Hackathon", "First place")),
				List.of(new SkillItem("Languages", "Java, SQL")), locale, 1);
	}

	@Test
	void aRoleIsWrittenAsTheTemplateTypesetsIt() {
		String text = new ResumePlainTextRenderer().render(resume("en"));

		// title and dates on one line, the company under them (see the \role macro of ResumeLatexRenderer)
		assertTrue(text.contains("Senior Engineer — 2020 – 2024\nAcme Corp, Paris\n- Built a gateway"), text);
		assertTrue(text.contains("BSc Computer Science — Munich\nTU Munich\nWith distinction"), text);
	}

	@Test
	void theHeadingsAreTheOnesOfThePdf() {
		String english = new ResumePlainTextRenderer().render(resume("en"));
		String french = new ResumePlainTextRenderer().render(resume("fr"));

		for (String heading : List.of("Profile", "Professional Experience", "Education", "Achievements", "Skills")) {
			assertTrue(english.contains("\n" + heading + "\n"), heading + " in\n" + english);
		}
		for (String heading : List.of("Profil", "Expérience professionnelle", "Réalisations", "Compétences")) {
			assertTrue(french.contains("\n" + heading + "\n"), heading + " in\n" + french);
		}
		assertEquals(1, english.lines().filter("Education"::equals).count());
	}
}
