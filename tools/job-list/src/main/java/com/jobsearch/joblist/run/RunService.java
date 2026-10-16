package com.jobsearch.joblist.run;

import com.jobsearch.joblist.profile.SearchProfile;
import java.time.Instant;
import java.time.Duration;
import com.jobsearch.joblist.config.JobListProperties;
import com.jobsearch.joblist.common.TooManyRequestsException;
import com.jobsearch.joblist.profile.SearchProfileService;
import com.jobsearch.joblist.run.RunDtos.RunResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Starting runs and reading them back, for the calling client's profiles. */
@Service
public class RunService {

	private static final List<RunStatus> ACTIVE = List.of(RunStatus.QUEUED, RunStatus.RUNNING);

	private final RunRepository runs;
	private final RunSourceRepository runSources;
	private final SearchProfileService profiles;
	private final JobListProperties properties;
	private final RunStarts starts;

	public RunService(RunRepository runs, RunSourceRepository runSources, SearchProfileService profiles, JobListProperties properties, RunStarts starts) {
		this.runs = runs;
		this.runSources = runSources;
		this.profiles = profiles;
		this.properties = properties;
		this.starts = starts;
	}

	/** Queues a run, or returns the one already waiting or running for the profile. */
	public RunResponse start(String client, long profileId) {
		SearchProfile profile = profiles.owned(client, profileId);
		return RunDtos.of(enqueue(client, profile), List.of());
	}

	private Run enqueue(String client, SearchProfile profile) {
		long profileId = profile.getId();
		return runs.findFirstByProfileIdAndStatusIn(profileId, ACTIVE).orElseGet(() -> {
			checkLimits(client, profile.getOwnerRef());
			try {
				Run run = runs.saveAndFlush(Run.queued(profileId));
				starts.record(client, profile.getOwnerRef());
				return run;
			}
			catch (DataIntegrityViolationException ex) {
				// Another request queued it first (the unique index allows one unfinished run per profile).
				return runs.findFirstByProfileIdAndStatusIn(profileId, ACTIVE).orElseThrow(() -> ex);
			}
		});
	}

	/**
	 * Every new run can spend Apify and model money, and a client (an agent in a loop) chooses how often to ask and under
	 * which owner names, so a day's runs are limited per owner and per client. The count lives in {@link RunStarts}, which
	 * a deleted profile does not take with it.
	 */
	private void checkLimits(String client, String ownerRef) {
		Instant since = Instant.now().minus(Duration.ofDays(1));
		if (starts.byOwnerSince(client, ownerRef, since) >= properties.maxRunsPerOwnerPerDay()) {
			throw new TooManyRequestsException("This owner has already started " + properties.maxRunsPerOwnerPerDay() + " runs in the last 24 hours");
		}
		if (starts.byClientSince(client, since) >= properties.maxRunsPerClientPerDay()) {
			throw new TooManyRequestsException("This client has already started " + properties.maxRunsPerClientPerDay() + " runs in the last 24 hours");
		}
	}

	public List<RunResponse> recent(String client, long profileId) {
		profiles.owned(client, profileId);
		List<Run> latest = runs.findTop20ByProfileIdOrderByIdDesc(profileId);
		Map<Long, List<RunSource>> sourcesByRun = runSources.findByRunIdIn(latest.stream().map(Run::getId).toList()).stream()
				.collect(Collectors.groupingBy(RunSource::getRunId));
		return latest.stream().map(run -> RunDtos.of(run, sourcesByRun.getOrDefault(run.getId(), List.of()))).toList();
	}
}
