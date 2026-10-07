package com.jobsearch.core_api.compile;

import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.compile.CompileDtos.CompileError;
import com.jobsearch.core_api.compile.CompileDtos.CompileJobResponse;
import com.jobsearch.core_api.resume.ResumeService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Accepts resume compile requests and reports their progress. The compiling itself happens later in
 * {@link CompileDispatcher}; nothing here calls the worker.
 */
@Service
public class CompileJobService {

	private static final Logger log = LoggerFactory.getLogger(CompileJobService.class);
	private static final List<CompileJobStatus> ACTIVE = List.of(CompileJobStatus.QUEUED, CompileJobStatus.RUNNING);

	private final CompileJobRepository jobRepository;
	private final ResumePdfCacheRepository cacheRepository;
	private final ResumeService resumeService;
	private final CurrentUserService currentUserService;

	public CompileJobService(
			CompileJobRepository jobRepository,
			ResumePdfCacheRepository cacheRepository,
			ResumeService resumeService,
			CurrentUserService currentUserService
	) {
		this.jobRepository = jobRepository;
		this.cacheRepository = cacheRepository;
		this.resumeService = resumeService;
		this.currentUserService = currentUserService;
	}

	/**
	 * Not transactional on purpose: if a parallel request wins the insert race, the unique-index
	 * violation would abort a surrounding transaction and the follow-up lookup could not run.
	 */
	public CompileJobResponse enqueue() {
		long userId = currentUserService.requireUserId();
		String source = resumeService.toLatex();
		String hash = sha256(source);

		if (cacheRepository.touch(hash) > 0) {
			CompileJob job = jobRepository.save(CompileJob.doneFromCache(userId, hash));
			log.info("Compile enqueued jobId={} userId={} cacheHit=true", job.getId(), userId);
			return toResponse(job);
		}

		CompileJob job = jobRepository.findFirstByUserIdAndSourceHashAndStatusIn(userId, hash, ACTIVE)
				.orElseGet(() -> insertQueued(userId, hash, source));
		log.info("Compile enqueued jobId={} userId={} cacheHit=false", job.getId(), userId);
		return toResponse(job);
	}

	public CompileJobResponse get(UUID jobId) {
		return toResponse(getOwnedJob(jobId));
	}

	public byte[] pdf(UUID jobId) {
		CompileJob job = getOwnedJob(jobId);
		if (job.getStatus() != CompileJobStatus.DONE) {
			throw new ConflictException("PDF is not ready, job status is " + job.getStatus());
		}
		return cacheRepository.findById(job.getSourceHash())
				.map(ResumePdfCache::getPdf)
				// Cached PDFs unused for 30 days are deleted by CompileJanitor.
				.orElseThrow(() -> new NotFoundException("PDF has expired, compile the resume again"));
	}

	private CompileJob insertQueued(long userId, String hash, String source) {
		try {
			return jobRepository.saveAndFlush(CompileJob.queued(userId, hash, source));
		}
		catch (DataIntegrityViolationException ex) {
			// A parallel request queued the same source first (uq_compile_job_active).
			return jobRepository.findFirstByUserIdAndSourceHashAndStatusIn(userId, hash, ACTIVE)
					.orElseThrow(() -> ex);
		}
	}

	private CompileJob getOwnedJob(UUID jobId) {
		return jobRepository.findByIdAndUserId(jobId, currentUserService.requireUserId())
				.orElseThrow(() -> new NotFoundException("Compile job not found: " + jobId));
	}

	private static CompileJobResponse toResponse(CompileJob job) {
		CompileError error = job.getErrorCode() == null
				? null
				: new CompileError(job.getErrorCode(), job.getErrorMessage());
		return new CompileJobResponse(
				job.getId().toString(),
				job.getStatus().name(),
				error,
				job.getCreatedAt().toString(),
				job.getFinishedAt() == null ? null : job.getFinishedAt().toString()
		);
	}

	private static String sha256(String source) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}
}
