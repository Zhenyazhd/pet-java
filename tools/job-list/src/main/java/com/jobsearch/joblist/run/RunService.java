package com.jobsearch.joblist.run;

import com.jobsearch.joblist.profile.SearchProfile;
import java.time.Instant;
import java.time.Duration;
import com.jobsearch.joblist.config.JobListProperties;
import com.jobsearch.joblist.common.ConflictException;
import com.jobsearch.joblist.common.TooManyRequestsException;
import com.jobsearch.joblist.rank.RankCandidates;
import com.jobsearch.joblist.profile.SearchProfileService;
import com.jobsearch.joblist.run.RunDtos.RunResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
	private final RankCandidates rankCandidates;
	private final SpendLedger ledger;

	public RunService(RunRepository runs, RunSourceRepository runSources, SearchProfileService profiles, JobListProperties properties, RunStarts starts, RankCandidates rankCandidates, SpendLedger ledger) {
		this.runs = runs;
		this.runSources = runSources;
		this.profiles = profiles;
		this.properties = properties;
		this.starts = starts;
		this.rankCandidates = rankCandidates;
		this.ledger = ledger;
	}

	/** Queues a run, or returns the one already waiting or running for the profile. */
	public RunResponse start(String client, long profileId) {
		SearchProfile profile = profiles.owned(client, profileId);
		return RunDtos.of(enqueue(client, profile), List.of());
	}

	/**
	 * Queues a rank-only run (no source, no Apify), or returns the one already waiting or running for the profile.
	 * 409 when there is nothing to rank; the daily cap is the rank-only one, apart from the full-run limits.
	 */
	public RunResponse startRankOnly(String client, long profileId) {
		SearchProfile profile = profiles.owned(client, profileId);
		return RunDtos.of(enqueue(client, profile, RunMode.RANK_ONLY), List.of());
	}

	/** How many stored postings of the profile still wait for a score (not capped by max-ranked-per-run). */
	public int unranked(String client, long profileId) {
		return rankCandidates.count(profiles.owned(client, profileId));
	}

	private Run enqueue(String client, SearchProfile profile) {
		return enqueue(client, profile, RunMode.FULL);
	}

	private Run enqueue(String client, SearchProfile profile, RunMode mode) {
		long profileId = profile.getId();
		Optional<Run> active = runs.findFirstByProfileIdAndStatusIn(profileId, ACTIVE);
		if (active.isPresent()) {
			return sameKind(active.get(), mode);
		}
		if (mode == RunMode.RANK_ONLY) {
			if (rankCandidates.count(profile) == 0) {
				throw new ConflictException("Nothing to rank");
			}
			// Checked before any cap slot is taken, so a refused start costs the owner nothing.
			if (!ledger.llmHasRoom()) {
				throw new ConflictException("The monthly model budget is used up");
			}
			checkRankLimits(client, profile.getOwnerRef());
		}
		else {
			checkLimits(client, profile.getOwnerRef());
		}
		try {
			Run run = runs.saveAndFlush(Run.queued(profileId, mode));
			starts.record(client, profile.getOwnerRef(), mode);
			return run;
		}
		catch (DataIntegrityViolationException ex) {
			// Another request queued it first (the unique index allows one unfinished run per profile).
			return sameKind(runs.findFirstByProfileIdAndStatusIn(profileId, ACTIVE).orElseThrow(() -> ex), mode);
		}
	}

	/** The run already in progress is returned only when it is of the requested kind; a different kind is a conflict. */
	private static Run sameKind(Run active, RunMode requested) {
		if (active.getMode() != requested) {
			throw new ConflictException("A " + (active.getMode() == RunMode.FULL ? "full" : "ranking") + " run is already in progress for this profile");
		}
		return active;
	}

	/**
	 * Every new run can spend Apify and model money, and a client (an agent in a loop) chooses how often to ask and under
	 * which owner names, so a day's runs are limited per owner and per client. The count lives in {@link RunStarts}, which
	 * a deleted profile does not take with it.
	 */
	private void checkLimits(String client, String ownerRef) {
		Instant since = Instant.now().minus(Duration.ofDays(1));
		if (starts.byOwnerSince(client, ownerRef, RunMode.FULL, since) >= properties.maxRunsPerOwnerPerDay()) {
			throw new TooManyRequestsException("This owner has already started " + properties.maxRunsPerOwnerPerDay() + " runs in the last 24 hours");
		}
		if (starts.byClientSince(client, RunMode.FULL, since) >= properties.maxRunsPerClientPerDay()) {
			throw new TooManyRequestsException("This client has already started " + properties.maxRunsPerClientPerDay() + " runs in the last 24 hours");
		}
	}

	/**
	 * Rank-only runs cost model money only; they have their own caps per owner and per client and do not use up the
	 * full-run limits. Like the full-run limits this is check-then-insert, so concurrent starts can overshoot a cap by
	 * the number of requests in flight at that moment (bounded by the one unfinished run per profile and the request rate).
	 */
	private void checkRankLimits(String client, String ownerRef) {
		Instant since = Instant.now().minus(Duration.ofDays(1));
		if (starts.byOwnerSince(client, ownerRef, RunMode.RANK_ONLY, since) >= properties.maxRankRunsPerOwnerPerDay()) {
			throw new TooManyRequestsException("This owner has already started " + properties.maxRankRunsPerOwnerPerDay() + " ranking runs in the last 24 hours");
		}
		if (starts.byClientSince(client, RunMode.RANK_ONLY, since) >= properties.maxRankRunsPerClientPerDay()) {
			throw new TooManyRequestsException("This client has already started " + properties.maxRankRunsPerClientPerDay() + " ranking runs in the last 24 hours");
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
