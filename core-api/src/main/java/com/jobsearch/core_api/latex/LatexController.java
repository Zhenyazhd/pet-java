package com.jobsearch.core_api.latex;

import com.jobsearch.core_api.latex.LatexDtos.CompileRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Raw LaTeX → PDF compile endpoint (used by the free-form editor). */
@RestController
@RequestMapping("/api/latex")
public class LatexController {

	private final LatexCompileService latexCompileService;

	public LatexController(LatexCompileService latexCompileService) {
		this.latexCompileService = latexCompileService;
	}

	@PostMapping(value = "/compile", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> compile(@Valid @RequestBody CompileRequest request) {
		byte[] pdf = latexCompileService.compile(request.source());
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"document.pdf\"")
				.contentType(MediaType.APPLICATION_PDF)
				.body(pdf);
	}
}
