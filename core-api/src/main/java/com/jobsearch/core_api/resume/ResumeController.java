package com.jobsearch.core_api.resume;

import com.jobsearch.core_api.latex.LatexCompileService;
import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Structured resume CRUD and PDF compile. */
@RestController
@RequestMapping("/api/resume")
public class ResumeController {

	private final ResumeService resumeService;
	private final LatexCompileService latexCompileService;

	public ResumeController(ResumeService resumeService, LatexCompileService latexCompileService) {
		this.resumeService = resumeService;
		this.latexCompileService = latexCompileService;
	}

	@GetMapping
	public ResumeDocument get() {
		return resumeService.get();
	}

	@PutMapping
	public ResumeDocument save(@Valid @RequestBody ResumeDocument resume) {
		return resumeService.save(resume);
	}

	@PostMapping(value = "/compile", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> compile() {
		byte[] pdf = latexCompileService.compile(resumeService.toLatex());
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"resume.pdf\"")
				.contentType(MediaType.APPLICATION_PDF)
				.body(pdf);
	}
}
