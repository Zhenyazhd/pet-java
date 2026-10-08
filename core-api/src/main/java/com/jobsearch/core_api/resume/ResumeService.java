package com.jobsearch.core_api.resume;

import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.Hashes;
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
	/**
	 * What the editor loads. A stored value that is not a resume (the number 0 once was) must not lock the
	 * user out of their own CV page, so it reads as the template and the next save overwrites it.
	 */
	@Transactional(readOnly = true)
	public ResumeDocument getForEditing() {
		AppUser user = currentUserService.requireUser();
		try {
			return read(user);
		}
		catch (JacksonException ex) {
			log.error("Stored resume is not readable, serving the template. userId={} {}", user.getId(), describe(user, ex));
			return withVersion(defaultResume, user.getResumeVersion());
		}
	}

	/**
	 * What compiling, the AI and the ATS match work on. A damaged stored resume stops them: a PDF, a score or an
	 * edit made against the placeholder template would look right and be wrong.
	 */
	@Transactional(readOnly = true)
	public ResumeDocument get() {
		AppUser user = currentUserService.requireUser();
		try {
			return read(user);
		}
		catch (JacksonException ex) {
			log.warn("Stored resume is not readable. userId={} {}", user.getId(), describe(user, ex));
			throw new ConflictException("Your saved CV is damaged. Open the CV page and save it to repair it.");
		}
	}

	private ResumeDocument read(AppUser user) {
		if (user.getResumeJson() == null || user.getResumeJson().isBlank()) {
			log.debug("No saved resume for userId={}, returning default template", user.getId());
			return withVersion(defaultResume, user.getResumeVersion());
		}
		ResumeDocument stored = objectMapper.readValue(user.getResumeJson(), ResumeDocument.class);
		return withVersion(stored, user.getResumeVersion());
	}

	private static String describe(AppUser user, JacksonException ex) {
		String raw = user.getResumeJson();
		return "length=" + raw.length() + " sha256=" + Hashes.sha256(raw) + " problem=" + ex.getOriginalMessage();
	}

	/**
	 * Rejects a save whose {@code version} doesn't match the row's current {@code resumeVersion}
	 * (checked atomically by {@link AppUserRepository#updateResume}) —
	 * i.e. the edit was based on stale data (a second tab/session saved in between). Without this,
	 * a full-document overwrite would silently discard whatever the other save just wrote.
	 */
	public ResumeDocument save(ResumeDocument resume) {
		long userId = currentUserService.requireUserId();
		if (resume.version() == null) {
			throw staleResume();
		}
		int newVersion = resume.version() + 1;
		ResumeDocument toStore = withVersion(resume, newVersion);
		String json;
		try {
			json = objectMapper.writeValueAsString(toStore);
		}
		catch (JacksonException ex) {
			throw new IllegalStateException("Failed to serialize resume JSON", ex);
		}
		if (appUserRepository.updateResume(userId, json, resume.version()) == 0) {
			throw staleResume();
		}
		log.info("Saved resume for userId={} version={}", userId, newVersion);
		return toStore;
	}

	private static ConflictException staleResume() {
		return new ConflictException(
				"Resume was changed elsewhere since you loaded it — reload to see the latest version, "
						+ "then reapply your edits."
		);
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
