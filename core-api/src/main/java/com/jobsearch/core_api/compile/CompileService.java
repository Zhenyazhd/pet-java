package com.jobsearch.core_api.compile;

import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.Hashes;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.jobs.Job;
import com.jobsearch.core_api.jobs.JobDtos.JobResponse;
import com.jobsearch.core_api.jobs.JobService;
import com.jobsearch.core_api.jobs.JobStatus;
import com.jobsearch.core_api.jobs.JobType;
import com.jobsearch.core_api.resume.ResumeService;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Resume PDF requests: queue a build unless the PDF is cached, and hand out the finished PDF. */
@Service
public class CompileService {

	private final JobService jobService;
	private final ResumePdfCacheRepository cacheRepository;
	private final ResumeService resumeService;

	public CompileService(JobService jobService, ResumePdfCacheRepository cacheRepository, ResumeService resumeService) {
		this.jobService = jobService;
		this.cacheRepository = cacheRepository;
		this.resumeService = resumeService;
	}

	public JobResponse enqueue() {
		String source = resumeService.toLatex();
		String hash = Hashes.sha256(source);
		if (cacheRepository.touch(hash) > 0) {
			return jobService.enqueueDone(JobType.RESUME_PDF, hash);
		}
		return jobService.enqueue(JobType.RESUME_PDF, hash, source);
	}

	public byte[] pdf(UUID jobId) {
		Job job = jobService.getOwned(jobId, JobType.RESUME_PDF);
		if (job.getStatus() != JobStatus.DONE) {
			throw new ConflictException("PDF is not ready, job status is " + job.getStatus());
		}
		return cacheRepository.findById(job.getDedupeKey())
				.map(ResumePdfCache::getPdf)
				// Cached PDFs unused for 30 days are deleted by PdfCacheJanitor.
				.orElseThrow(() -> new NotFoundException("PDF has expired, compile the resume again"));
	}
}
