package com.jobsearch.core_api.compile;

import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.Hashes;
import com.jobsearch.core_api.jobs.Job;
import com.jobsearch.core_api.jobs.JobService;
import com.jobsearch.core_api.jobs.JobStatus;
import com.jobsearch.core_api.jobs.JobType;
import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import com.jobsearch.core_api.resume.ResumeLatexRenderer;
import com.jobsearch.core_api.resume.ResumeService;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Finds the PDF a user built and makes sure it still belongs to their current resume. */
@Service
public class BuiltPdfService {

	/** {@code sourceHash} is the key of the PDF in {@link ResumePdfCacheRepository}; {@code resume} is what it was built from. */
	public record BuiltPdf(String sourceHash, ResumeDocument resume) {
	}

	private final JobService jobService;
	private final ResumeService resumeService;
	private final ResumeLatexRenderer latexRenderer;

	public BuiltPdfService(JobService jobService, ResumeService resumeService, ResumeLatexRenderer latexRenderer) {
		this.jobService = jobService;
		this.resumeService = resumeService;
		this.latexRenderer = latexRenderer;
	}

	/**
	 * The current user's finished PDF build, if the resume has not changed since. Anything that works on the PDF
	 * together with the resume text needs the two to belong together.
	 *
	 * @throws ConflictException when the build is not finished or the resume was edited after it
	 */
	public BuiltPdf require(UUID compileJobId) {
		Job compile = jobService.getOwned(compileJobId, JobType.RESUME_PDF);
		if (compile.getStatus() != JobStatus.DONE) {
			throw new ConflictException("PDF is not ready, job status is " + compile.getStatus());
		}
		ResumeDocument resume = resumeService.get();
		String sourceHash = compile.getDedupeKey();
		if (!Hashes.sha256(latexRenderer.render(resume)).equals(sourceHash)) {
			throw new ConflictException("The resume changed after this PDF was built. Build the PDF again.");
		}
		return new BuiltPdf(sourceHash, resume);
	}
}
