package com.jobsearch.core_api.latex;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class LatexDtos {

	private LatexDtos() {
	}

	public record CompileRequest(
			@NotBlank
			@Size(max = 500_000)
			String source
	) {
	}
}
