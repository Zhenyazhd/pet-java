package com.jobsearch.joblist.job;

import com.jobsearch.joblist.config.JobListProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Saves fetched jobs once; a job already stored (same board id, or same fingerprint from another board) is skipped. */
@Component
public class JobStore {

	private static final Logger log = LoggerFactory.getLogger(JobStore.class);

	private final JobRepository repository;
	private final Duration retention;

	public JobStore(JobRepository repository, JobListProperties properties) {
		this.repository = repository;
		this.retention = Duration.ofDays(properties.retentionDays());
	}

	public int saveNew(List<Job> jobs, Instant fetchedAt) {
		int saved = 0;
		for (Job job : jobs) {
			if (isOlderThanRetention(job, fetchedAt)) {
				continue;
			}
			job.setCountries(job.getCountries().stream().map(country -> country.toUpperCase(Locale.ROOT)).toList());
			job.setFingerprint(JobFingerprint.of(job));
			job.setFetchedAt(fetchedAt);
			if (!isKnown(job) && insert(job)) {
				saved++;
			}
		}
		return saved;
	}

	private boolean isOlderThanRetention(Job job, Instant now) {
		return job.getPostedAt() != null && job.getPostedAt().isBefore(now.minus(retention));
	}

	private boolean isKnown(Job job) {
		return repository.existsBySourceAndExternalId(job.getSource(), job.getExternalId())
				|| repository.existsByFingerprint(job.getFingerprint());
	}

	private boolean insert(Job job) {
		try {
			repository.saveAndFlush(job);
			return true;
		}
		catch (DataIntegrityViolationException ex) {
			if (!String.valueOf(ex.getMostSpecificCause().getMessage()).contains("uq_job_")) {
				log.warn("A job was not saved source={} externalId={} error={}", job.getSource(), job.getExternalId(), ex.getMostSpecificCause().getMessage());
			}
			return false;
		}
	}
}
