package com.jobsearch.core_api.resume;

import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.profile.AppUser;
import com.jobsearch.core_api.profile.AppUserRepository;
import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Loads/saves structured resume JSON for the current user and renders it to LaTeX. */
@Service
@Transactional
public class ResumeService {

	private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

	private final CurrentUserService currentUserService;
	private final AppUserRepository appUserRepository;
	private final ObjectMapper objectMapper;
	private final ResumeLatexRenderer latexRenderer;
	private final ResumeDocument defaultResume;

	public ResumeService(
			CurrentUserService currentUserService,
			AppUserRepository appUserRepository,
			ObjectMapper objectMapper,
			ResumeLatexRenderer latexRenderer
	) {
		this.currentUserService = currentUserService;
		this.appUserRepository = appUserRepository;
		this.objectMapper = objectMapper;
		this.latexRenderer = latexRenderer;
		this.defaultResume = loadDefault();
	}

	/**
	 * The stored/default JSON never carries an authoritative version (old rows predate the
	 * column; the bundled default template has none at all) — {@code resumeVersion} in the DB
	 * is always the source of truth, so every response is stamped with it here, overriding
	 * whatever (if anything) was embedded in the JSON.
	 */
	@Transactional(readOnly = true)
	public ResumeDocument get() {
		AppUser user = currentUserService.requireUser();
		if (user.getResumeJson() == null || user.getResumeJson().isBlank()) {
			log.debug("No saved resume for userId={}, returning default template", user.getId());
			return withVersion(defaultResume, user.getResumeVersion());
		}
		try {
			ResumeDocument stored = objectMapper.readValue(user.getResumeJson(), ResumeDocument.class);
			return withVersion(stored, user.getResumeVersion());
		}
		catch (JacksonException ex) {
			throw new IllegalStateException("Stored resume JSON is invalid for userId=" + user.getId(), ex);
		}
	}

	/**
	 * Rejects a save whose {@code version} doesn't match the row's current {@code resumeVersion} —
	 * i.e. the edit was based on stale data (a second tab/session saved in between). Without this,
	 * a full-document overwrite would silently discard whatever the other save just wrote.
	 */
	public ResumeDocument save(ResumeDocument resume) {
		AppUser user = currentUserService.requireUser();
		int currentVersion = user.getResumeVersion();
		if (resume.version() == null || resume.version() != currentVersion) {
			throw new ConflictException(
					"Resume was changed elsewhere since you loaded it — reload to see the latest version, "
							+ "then reapply your edits."
			);
		}
		int newVersion = currentVersion + 1;
		ResumeDocument toStore = withVersion(resume, newVersion);
		try {
			user.setResumeJson(objectMapper.writeValueAsString(toStore));
		}
		catch (JacksonException ex) {
			throw new IllegalStateException("Failed to serialize resume JSON", ex);
		}
		user.setResumeVersion(newVersion);
		appUserRepository.save(user);
		log.info("Saved resume for userId={} version={}", user.getId(), newVersion);
		return toStore;
	}

	@Transactional(readOnly = true)
	public String toLatex() {
		return latexRenderer.render(get());
	}

	private static ResumeDocument withVersion(ResumeDocument doc, int version) {
		return new ResumeDocument(
				doc.name(), doc.headline(), doc.phone(), doc.email(), doc.linkedinUrl(), doc.linkedinLabel(),
				doc.profile(), doc.experience(), doc.education(), doc.achievements(), doc.skills(), doc.locale(),
				version
		);
	}

	private ResumeDocument loadDefault() {
		try (InputStream in = new ClassPathResource("default-resume.json").getInputStream()) {
			return objectMapper.readValue(in, ResumeDocument.class);
		}
		catch (IOException | JacksonException ex) {
			throw new IllegalStateException("Failed to load default-resume.json", ex);
		}
	}
}
