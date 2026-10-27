package com.jobsearch.core_api.resume;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobsearch.core_api.resume.ResumeDtos.EducationItem;
import com.jobsearch.core_api.resume.ResumeDtos.ExperienceItem;
import com.jobsearch.core_api.resume.ResumeDtos.LayoutSettings;
import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A list with a null in it passed validation and then broke compiling, matching and the AI. */
class ResumeValidationTest {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void aValidResumePasses() {
		assertTrue(validator.validate(resume(List.of(job(List.of("built a thing"))))).isEmpty());
	}

	@Test
	void aNullJobIsRejected() {
		List<ExperienceItem> jobs = new ArrayList<>();
		jobs.add(null);

		assertTrue(!validator.validate(resume(jobs)).isEmpty());
	}

	@Test
	void aNullBulletIsRejected() {
		List<String> bullets = new ArrayList<>();
		bullets.add(null);

		assertTrue(!validator.validate(resume(List.of(job(bullets)))).isEmpty());
	}

	@Test
	void aNullEducationEntryIsRejected() {
		List<EducationItem> education = new ArrayList<>();
		education.add(null);

		ResumeDocument resume = new ResumeDocument("Ada", "", "", "", "", "", "", List.of(), education, List.of(), List.of(), "en", 0);

		assertTrue(!validator.validate(resume).isEmpty());
	}

	@Test
	void layoutSettingsInRangeOrAbsentPass() {
		assertTrue(validator.validate(withLayout(new LayoutSettings(null, null, null, null, null, null, null, null, null, null, null, null, null, null))).isEmpty());
		assertTrue(validator.validate(withLayout(new LayoutSettings(8.0, 0.2, 0.0, 3.0, 1.5, 1.5, 2.0, 4.0, 0.3, 0.3, false, null, null, null))).isEmpty());
	}

	@Test
	void layoutSettingsOutOfRangeAreRejected() {
		assertTrue(!validator.validate(withLayout(new LayoutSettings(7.9, null, null, null, null, null, null, null, null, null, null, null, null, null))).isEmpty());
		assertTrue(!validator.validate(withLayout(new LayoutSettings(null, null, null, null, null, null, null, 4.1, null, null, null, null, null, null))).isEmpty());
		assertTrue(!validator.validate(withLayout(new LayoutSettings(null, null, null, -1.0, null, null, null, null, null, null, null, null, null, null))).isEmpty());
	}

	@Test
	void templateAndSpacingSettingsAreChecked() {
		assertTrue(validator.validate(withLayout(new LayoutSettings(null, null, null, null, null, null, null, null, null, null, null, "charter", 0.8, 1.5))).isEmpty());
		assertTrue(!validator.validate(withLayout(new LayoutSettings(null, null, null, null, null, null, null, null, null, null, null, "gothic", null, null))).isEmpty());
		assertTrue(!validator.validate(withLayout(new LayoutSettings(null, null, null, null, null, null, null, null, null, null, null, null, 2.1, null))).isEmpty());
		assertTrue(!validator.validate(withLayout(new LayoutSettings(null, null, null, null, null, null, null, null, null, null, null, null, null, 3.1))).isEmpty());
	}

	private static ResumeDocument withLayout(LayoutSettings layout) {
		return new ResumeDocument("Ada", "", "", "", "", "", "", List.of(), List.of(), List.of(), List.of(), "en", 0, layout);
	}

	private static ExperienceItem job(List<String> bullets) {
		return new ExperienceItem("Engineer", "Acme", "2020", bullets);
	}

	private static ResumeDocument resume(List<ExperienceItem> jobs) {
		return new ResumeDocument("Ada", "", "", "", "", "", "", jobs, List.of(), List.of(), List.of(), "en", 0);
	}
}
