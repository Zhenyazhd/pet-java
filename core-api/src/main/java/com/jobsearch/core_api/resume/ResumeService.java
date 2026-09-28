package com.jobsearch.core_api.resume;

import com.jobsearch.core_api.auth.CurrentUserService;
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

	@Transactional(readOnly = true)
	public ResumeDocument get() {
		AppUser user = currentUserService.requireUser();
		if (user.getResumeJson() == null || user.getResumeJson().isBlank()) {
			log.debug("No saved resume for userId={}, returning default template", user.getId());
			return defaultResume;
		}
		return objectMapper.readValue(user.getResumeJson(), ResumeDocument.class);
	}

	public ResumeDocument save(ResumeDocument resume) {
		AppUser user = currentUserService.requireUser();
		user.setResumeJson(objectMapper.writeValueAsString(resume));
		appUserRepository.save(user);
		log.info("Saved resume for userId={}", user.getId());
		return resume;
	}

	@Transactional(readOnly = true)
	public String toLatex() {
		return latexRenderer.render(get());
	}

	private ResumeDocument loadDefault() {
		try (InputStream in = new ClassPathResource("default-resume.json").getInputStream()) {
			return objectMapper.readValue(in, ResumeDocument.class);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to load default-resume.json", ex);
		}
	}
}
