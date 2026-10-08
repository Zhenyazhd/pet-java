package com.jobsearch.core_api.resume;

import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Structured resume CRUD; PDF builds go through {@code CompileController}. */
@RestController
@RequestMapping("/api/resume")
public class ResumeController {

	private final ResumeService resumeService;

	public ResumeController(ResumeService resumeService) {
		this.resumeService = resumeService;
	}

	@GetMapping
	public ResumeDocument get() {
		return resumeService.getForEditing();
	}

	@PutMapping
	public ResumeDocument save(@Valid @RequestBody ResumeDocument resume) {
		return resumeService.save(resume);
	}
}
