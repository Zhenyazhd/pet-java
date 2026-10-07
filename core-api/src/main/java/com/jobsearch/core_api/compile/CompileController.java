package com.jobsearch.core_api.compile;

import com.jobsearch.core_api.compile.CompileDtos.CompileJobResponse;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Queue a PDF build of the current resume, poll its status, download the result. */
@RestController
@RequestMapping("/api/resume/compile")
public class CompileController {

	private final CompileJobService compileJobService;

	public CompileController(CompileJobService compileJobService) {
		this.compileJobService = compileJobService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public CompileJobResponse enqueue() {
		return compileJobService.enqueue();
	}

	@GetMapping("/{jobId}")
	public CompileJobResponse get(@PathVariable UUID jobId) {
		return compileJobService.get(jobId);
	}

	@GetMapping("/{jobId}/pdf")
	public ResponseEntity<byte[]> pdf(@PathVariable UUID jobId) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"resume.pdf\"")
				.contentType(MediaType.APPLICATION_PDF)
				.body(compileJobService.pdf(jobId));
	}
}
