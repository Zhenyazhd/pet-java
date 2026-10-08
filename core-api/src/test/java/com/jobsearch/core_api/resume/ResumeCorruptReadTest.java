package com.jobsearch.core_api.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jobsearch.core_api.TestSupport;
import com.jobsearch.core_api.TestcontainersConfiguration;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.profile.AppUserRepository;
import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** A stored value that is not a resume (the number 0 once was) must not lock the user out of the page. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ResumeCorruptReadTest {

	@Autowired
	private ResumeService resumeService;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void theEditorReadsACorruptStoredResumeAsTheTemplateWithTheLiveVersion() {
		long userId = TestSupport.signInNewUser(userRepository);
		jdbc.update("update app_user set resume_json = '0', resume_version = 7 where id = ?", userId);

		ResumeDocument resume = resumeService.getForEditing();

		assertNotNull(resume.name());
		assertEquals(7, resume.version());
	}

	@Test
	void everythingThatConsumesTheResumeRefusesInsteadOfWorkingOnTheTemplate() {
		long userId = TestSupport.signInNewUser(userRepository);
		jdbc.update("update app_user set resume_json = '0', resume_version = 7 where id = ?", userId);

		assertThrows(ConflictException.class, () -> resumeService.get());
		assertThrows(ConflictException.class, () -> resumeService.toLatex());
	}

	@Test
	void aStaleVersionIsStillRejectedOnACorruptRow() {
		long userId = TestSupport.signInNewUser(userRepository);
		jdbc.update("update app_user set resume_json = '0', resume_version = 7 where id = ?", userId);
		ResumeDocument template = resumeService.getForEditing();

		assertThrows(ConflictException.class, () -> resumeService.save(withVersion(template, 6)));
	}

	@Test
	void theUserCanSaveOverACorruptResume() {
		long userId = TestSupport.signInNewUser(userRepository);
		jdbc.update("update app_user set resume_json = '[1,2', resume_version = 3 where id = ?", userId);

		ResumeDocument template = resumeService.getForEditing();
		ResumeDocument saved = resumeService.save(template);

		assertEquals(4, saved.version());
		assertEquals(saved.name(), resumeService.get().name());
		assertEquals(4, resumeService.get().version());
	}

	private static ResumeDocument withVersion(ResumeDocument doc, int version) {
		return new ResumeDocument(
				doc.name(), doc.headline(), doc.phone(), doc.email(), doc.linkedinUrl(), doc.linkedinLabel(),
				doc.profile(), doc.experience(), doc.education(), doc.achievements(), doc.skills(), doc.locale(), version);
	}
}
