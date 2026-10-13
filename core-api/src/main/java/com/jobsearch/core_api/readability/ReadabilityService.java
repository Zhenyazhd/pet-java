package com.jobsearch.core_api.readability;

import com.jobsearch.core_api.common.ServiceUnavailableException;
import com.jobsearch.core_api.compile.BuiltPdfService;
import com.jobsearch.core_api.compile.BuiltPdfService.BuiltPdf;
import com.jobsearch.core_api.config.AppProperties;
import com.jobsearch.core_api.jobs.JobDtos.JobResponse;
import com.jobsearch.core_api.jobs.JobService;
import com.jobsearch.core_api.jobs.JobType;
import com.jobsearch.core_api.resume.ResumePlainTextRenderer;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Queues a readability check of a built PDF; {@link ReadabilityJobHandler} runs it on the ats-worker. */
@Service
public class ReadabilityService {

	private final JobService jobService;
	private final BuiltPdfService builtPdfService;
	private final ResumePlainTextRenderer plainTextRenderer;
	private final ObjectMapper objectMapper;
	private final boolean enabled;

	public ReadabilityService(
			JobService jobService,
			BuiltPdfService builtPdfService,
			ResumePlainTextRenderer plainTextRenderer,
			ObjectMapper objectMapper,
			AppProperties appProperties
	) {
		this.jobService = jobService;
		this.builtPdfService = builtPdfService;
		this.plainTextRenderer = plainTextRenderer;
		this.objectMapper = objectMapper;
		this.enabled = appProperties.getAtsWorker().isEnabled();
	}

	public JobResponse enqueue(UUID compileJobId) {
		if (!enabled) {
			// A fixed answer with no Retry-After: waiting will not help. 503 is not 404, which means "no such job".
			throw new ServiceUnavailableException("The PDF readability check is not available yet.", null);
		}
		// The check compares the PDF with the resume text; they only belong together while the resume is unchanged.
		BuiltPdf built = builtPdfService.require(compileJobId);
		String sourceHash = built.sourceHash();
		String payload = objectMapper.writeValueAsString(
				new ReadabilityPayload(sourceHash, plainTextRenderer.render(built.resume())));
		return jobService.enqueue(JobType.PDF_READABILITY, sourceHash, payload);
	}
}
