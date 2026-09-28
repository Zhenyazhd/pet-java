package com.jobsearch.core_api.resume;

import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.config.AppProperties;
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

	private final AppProperties appProperties;
	private final AppUserRepository appUserRepository;
	private final ObjectMapper objectMapper;
	private final ResumeLatexRenderer latexRenderer;
	private final ResumeDocument defaultResume;

	public ResumeService(
			AppProperties appProperties,
			AppUserRepository appUserRepository,
			ObjectMapper objectMapper,
			ResumeLatexRenderer latexRenderer
	) {
		this.appProperties = appProperties;
		this.appUserRepository = appUserRepository;
		this.objectMapper = objectMapper;
		this.latexRenderer = latexRenderer;
		this.defaultResume = loadDefault();
	}

	@Transactional(readOnly = true)
	public ResumeDocument get() {
		AppUser user = currentUser();
		if (user.getResumeJson() == null || user.getResumeJson().isBlank()) {
			log.debug("No saved resume for userId={}, returning default template", user.getId());
			return defaultResume;
		}
		return objectMapper.readValue(user.getResumeJson(), ResumeDocument.class);
	}

	public ResumeDocument save(ResumeDocument resume) {
		AppUser user = currentUser();
		user.setResumeJson(objectMapper.writeValueAsString(resume));
		appUserRepository.save(user);
		log.info("Saved resume for userId={}", user.getId());
		return resume;
	}

	@Transactional(readOnly = true)
	public String toLatex() {
		return latexRenderer.render(get());
	}

	private AppUser currentUser() {
		long userId = appProperties.getCurrentUserId();
		return appUserRepository.findById(userId)
				.orElseThrow(() -> new NotFoundException("User not found: " + userId));
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
