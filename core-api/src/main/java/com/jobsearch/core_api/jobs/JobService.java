package com.jobsearch.core_api.jobs;

import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.jobs.JobDtos.JobError;
import com.jobsearch.core_api.jobs.JobDtos.JobResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Creates jobs for the current user and reports on them; the running happens in {@link JobDispatcher}. */
@Service
public class JobService {

	private static final Logger log = LoggerFactory.getLogger(JobService.class);
	private static final List<JobStatus> ACTIVE = List.of(JobStatus.QUEUED, JobStatus.RUNNING);

	private final JobRepository jobRepository;
	private final CurrentUserService currentUserService;
	private final ObjectMapper objectMapper;

	public JobService(JobRepository jobRepository, CurrentUserService currentUserService, ObjectMapper objectMapper) {
		this.jobRepository = jobRepository;
		this.currentUserService = currentUserService;
		this.objectMapper = objectMapper;
	}

	/**
	 * Queues a job, or returns the user's active job for the same type and key (a double click).
	 * Not transactional on purpose: if a parallel request wins the insert race, the unique-index
	 * violation would abort a surrounding transaction and the follow-up lookup could not run.
	 */
	public JobResponse enqueue(JobType type, String dedupeKey, String payload) {
		long userId = currentUserService.requireUserId();
		Optional<Job> active = jobRepository.findFirstByUserIdAndTypeAndDedupeKeyAndStatusIn(userId, type, dedupeKey, ACTIVE);
		Job job = active.orElseGet(() -> insertQueued(type, userId, dedupeKey, payload));
		log.info("Job enqueued type={} jobId={} userId={} reused={}", type, job.getId(), userId, active.isPresent());
		return toResponse(job);
	}

	/** Records a request that needs no work (e.g. its PDF is already cached), so the client flow stays the same. */
	public JobResponse enqueueDone(JobType type, String dedupeKey) {
		long userId = currentUserService.requireUserId();
		Job job = jobRepository.save(Job.done(type, userId, dedupeKey));
		log.info("Job enqueued type={} jobId={} userId={} alreadyDone=true", type, job.getId(), userId);
		return toResponse(job);
	}

	public JobResponse get(UUID jobId, JobType type) {
		return toResponse(getOwned(jobId, type));
	}

	/** The current user's job of this type; anything else is reported as missing, not forbidden. */
	public Job getOwned(UUID jobId, JobType type) {
		return jobRepository.findByIdAndUserIdAndType(jobId, currentUserService.requireUserId(), type)
				.orElseThrow(() -> new NotFoundException("Job not found: " + jobId));
	}

	private Job insertQueued(JobType type, long userId, String dedupeKey, String payload) {
		try {
			return jobRepository.saveAndFlush(Job.queued(type, userId, dedupeKey, payload));
		}
		catch (DataIntegrityViolationException ex) {
			// A parallel request queued the same job first (uq_background_job_active).
			return jobRepository.findFirstByUserIdAndTypeAndDedupeKeyAndStatusIn(userId, type, dedupeKey, ACTIVE)
					.orElseThrow(() -> ex);
		}
	}

	private JobResponse toResponse(Job job) {
		JobError error = job.getErrorCode() == null ? null : new JobError(job.getErrorCode(), job.getErrorMessage());
		return new JobResponse(
				job.getId().toString(),
				job.getStatus().name(),
				error,
				job.getResult() == null ? null : objectMapper.readTree(job.getResult()),
				job.getCreatedAt().toString(),
				job.getFinishedAt() == null ? null : job.getFinishedAt().toString()
		);
	}
}
