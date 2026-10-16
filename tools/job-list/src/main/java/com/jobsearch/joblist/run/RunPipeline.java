package com.jobsearch.joblist.run;

import com.jobsearch.joblist.config.JobListProperties;
import com.jobsearch.joblist.source.SourceQuery;
import com.jobsearch.joblist.job.Job;
import com.jobsearch.joblist.job.JobRepository;
import com.jobsearch.joblist.job.JobStore;
import com.jobsearch.joblist.profile.SearchProfile;
import com.jobsearch.joblist.profile.SearchProfileRepository;
import com.jobsearch.joblist.profile.SearchProfileService;
import com.jobsearch.joblist.rank.JobRanker;
import com.jobsearch.joblist.rank.MatchRepository;
import com.jobsearch.joblist.rank.RankFailures;
import com.jobsearch.joblist.rank.RankReport;
import com.jobsearch.joblist.source.JobSource;
import com.jobsearch.joblist.source.SourceName;
import com.jobsearch.joblist.source.SourceResult;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** One run of a profile: fetch from each source, store the new jobs, rank the candidates. */
@Component
public class RunPipeline {

	private static final Logger log = LoggerFactory.getLogger(RunPipeline.class);

	private final RunRepository runs;
	private final RunSourceRepository runSources;
	private final SearchProfileRepository profiles;
	private final JobRepository jobs;
	private final JobStore jobStore;
	private final MatchRepository matches;
	private final SearchProfileService profileService;
	private final JobRanker ranker;
	private final SpendLedger ledger;
	private final RankFailures rankFailures;
	private final JobListProperties properties;
	private final Map<SourceName, JobSource> sources;

	public RunPipeline(
			RunRepository runs,
			RunSourceRepository runSources,
			SearchProfileRepository profiles,
			JobRepository jobs,
			JobStore jobStore,
			MatchRepository matches,
			SearchProfileService profileService,
			JobRanker ranker,
			SpendLedger ledger,
			RankFailures rankFailures,
			JobListProperties properties,
			List<JobSource> jobSources
	) {
		this.runs = runs;
		this.runSources = runSources;
		this.profiles = profiles;
		this.jobs = jobs;
		this.jobStore = jobStore;
		this.matches = matches;
		this.profileService = profileService;
		this.ranker = ranker;
		this.ledger = ledger;
		this.rankFailures = rankFailures;
		this.properties = properties;
		this.sources = jobSources.stream().collect(Collectors.toMap(JobSource::name, Function.identity()));
	}

	/**
	 * How long a claimed run may go on without a sign of life: the slowest single step (an Apify run or a model call)
	 * plus a margin for the HTTP calls around it. The lease is renewed before every step, so it never has to cover the whole run.
	 */
	public Duration stepLease() {
		Duration slowest = properties.apify().runTimeout().compareTo(properties.llm().timeout()) > 0
				? properties.apify().runTimeout() : properties.llm().timeout();
		// Apify calls have their own HTTP timeouts (start, polls with retries, abort, the dataset), which add up beyond
		// the run timeout when Apify is slow.
		return slowest.plus(Duration.ofMinutes(10));
	}

	/** Runs a claimed run to its end; never throws, the outcome is on the run row. */
	public void execute(Run run) {
		MDC.put("runId", String.valueOf(run.getId()));
		boolean released = false;
		try {
			SearchProfile profile = profiles.findById(run.getProfileId()).orElseThrow();
			List<SourceStatus> statuses = new ArrayList<>();
			for (String name : profile.getSources()) {
				renewLease(run);
				statuses.add(fetchSource(run, profile, sources.get(SourceName.valueOf(name))));
			}
			renewLease(run);
			RankReport ranking = rankCandidates(run, profile);
			run.addRanked(ranking.ranked(), ranking.costUsd());
			finish(run, statuses, ranking.problem());
		}
		catch (RunLostException ex) {
			// The janitor gave this run up (the lease ran out); whoever started a new run owns the profile now.
			log.warn("The run was released while it worked, stopping runId={}", run.getId());
			released = true;
		}
		catch (RuntimeException ex) {
			log.error("Run crashed", ex);
			run.finish(RunStatus.FAILED, "Unexpected error: " + ex.getClass().getSimpleName());
		}
		finally {
			if (!released) {
				saveFinished(run);
			}
			MDC.remove("runId");
		}
	}

	/** Every model call: proof of life, then a reservation in the ledger; afterwards the real cost is booked. */
	private JobRanker.CallGuard callGuard(Run run, SearchProfile profile) {
		return new JobRanker.CallGuard() {
			@Override
			public Ticket beforeCall() {
				renewLease(run);
				SpendLedger.Reservation reservation = ledger.reserveLlmCall(run.getId(), profile.getId());
				if (reservation.runReleased()) {
					throw new RunLostException(run.getId());
				}
				return new Ticket(reservation.id(), reservation.refusal());
			}

			@Override
			public void afterCall(Ticket ticket, BigDecimal costUsd, boolean costKnown) {
				try {
					if (costKnown) {
						ledger.settleLlm(ticket.reservationId(), costUsd);
						if (costUsd.compareTo(properties.llm().maxCostPerCallUsd()) > 0) {
							log.warn("A model call cost more than it was reserved at costUsd={} reservedUsd={}: raise JOB_LIST_LLM_MAX_COST_PER_CALL_USD",
									costUsd, properties.llm().maxCostPerCallUsd());
						}
					}
					else {
						// The provider did not say what it charged (or the call may have been billed without an answer).
						ledger.settleLlmUnknown(ticket.reservationId());
					}
				}
				catch (RuntimeException ex) {
					log.error("The model call could not be settled, the reservation stays at the cap error={}", ex.toString());
				}
			}
		};
	}

	/** Proof of life; 0 rows updated means the run is no longer RUNNING, and the worker must stop. */
	private void renewLease(Run run) {
		if (runs.renewLease(run.getId(), Instant.now().plus(stepLease())) == 0) {
			throw new RunLostException(run.getId());
		}
	}

	/**
	 * The result is written only while the run is still RUNNING, so a run the janitor released stays released. The
	 * profile may also have been deleted while the run was going; then its run is gone and there is nothing to record.
	 */
	private void saveFinished(Run run) {
		try {
			int rows = runs.finishIfRunning(run.getId(), run.getStatus(), run.getFinishedAt(), run.getFetched(), run.getNewJobs(),
					run.getRanked(), run.getCostUsd(), run.getError());
			if (rows == 0) {
				log.warn("The run is no longer running, its result was discarded runId={}", run.getId());
			}
		}
		catch (RuntimeException ex) {
			log.warn("The finished run could not be saved runId={} error={}", run.getId(), ex.toString());
		}
	}

	private SourceStatus fetchSource(Run run, SearchProfile profile, JobSource source) {
		Instant now = Instant.now();
		SourceQuery query = profileService.query(profile);
		String skipReason = skipReason(source, query);
		if (skipReason != null) {
			return skipped(run, source, skipReason, now);
		}
		String queryKey = source.queryKey(query);
		Instant lastSuccess = ledger.lastSuccess(source.name(), queryKey);
		query.setWindow(LookbackWindow.days(lastSuccess, now, properties.firstRunLookbackDays()), properties.maxItemsPerSource());

		Duration interval = Duration.ofHours(properties.source(source.name()).minIntervalHours());
		SpendLedger.Reservation reservation = ledger.reserveApify(run.getId(), profile.getId(), source.name(), queryKey, interval);
		if (reservation.runReleased()) {
			throw new RunLostException(run.getId());
		}
		if (!reservation.granted()) {
			return skipped(run, source, reservation.refusal(), now);
		}

		SourceResult result = fetchSafely(source, query);
		// The jobs are stored before the ledger says "succeeded", so a failure in between never leaves a success on the
		// ledger (which moves the next lookback window past them) for jobs that were not saved; and a failure to settle
		// never throws away what was paid for. A reservation left open is closed by the janitor as LOST, at the cap.
		int newJobs = 0;
		if (result.ok()) {
			try {
				newJobs = jobStore.saveNew(result.jobs(), now);
			}
			catch (RuntimeException ex) {
				log.error("The fetched jobs could not be stored source={} error={}", source.name(), ex.toString());
				result = SourceResult.failed(result.apifyRunId(), result.costUsd(), "The fetched jobs could not be stored");
			}
		}
		settleQuietly(reservation.id(), result);
		run.addFetched(result.fetched(), newJobs, result.costUsd());
		SourceStatus status = result.ok() ? SourceStatus.SUCCEEDED : SourceStatus.FAILED;
		runSources.save(RunSource.of(run.getId(), source.name(), status, now)
				.withResult(result.apifyRunId(), result.fetched(), newJobs, result.dropped(), result.costUsd(), result.error()));
		log.info("Source done source={} status={} fetched={} new={} costUsd={}", source.name(), status, result.fetched(), newJobs, result.costUsd());
		return status;
	}

	private void settleQuietly(long reservationId, SourceResult result) {
		try {
			ledger.settle(reservationId, result.ok(), result.costUsd(), result.apifyRunId());
		}
		catch (RuntimeException ex) {
			log.error("The spend could not be settled, the reservation stays at the cap reservationId={} error={}", reservationId, ex.toString());
		}
	}

	private SourceStatus skipped(Run run, JobSource source, String reason, Instant now) {
		runSources.save(RunSource.of(run.getId(), source.name(), SourceStatus.SKIPPED, now)
				.withResult(null, 0, 0, 0, BigDecimal.ZERO, reason));
		log.info("Source skipped source={} reason={}", source.name(), reason);
		return SourceStatus.SKIPPED;
	}

	/** Why the source cannot be called for this query at all, or null. The budget and the wait are decided by the ledger. */
	private String skipReason(JobSource source, SourceQuery query) {
		if (!properties.source(source.name()).enabled()) {
			return "source disabled";
		}
		if (!source.supports(query)) {
			return "query not supported";
		}
		// Jobs that cannot be ranked are not worth fetching: with the model budget gone, a fetch would only cost Apify money.
		return ledger.llmHasRoom() ? null : "model budget";
	}

	private SourceResult fetchSafely(JobSource source, SourceQuery query) {
		try {
			return source.fetch(query);
		}
		catch (RuntimeException ex) {
			log.error("Source crashed source={}", source.name(), ex);
			// Whether Apify had been called is not known, so the worst case is counted.
			return SourceResult.failed(null, properties.apify().maxCostPerSourceUsd(), "Unexpected error: " + ex.getClass().getSimpleName());
		}
	}

	/**
	 * The jobs to rank: stored recently, not yet ranked for this profile with the current
	 * prompt and expectations; newest first, up to the cap. This also covers jobs another profile's run stored and
	 * jobs a previous run had no room for.
	 */
	private RankReport rankCandidates(Run run, SearchProfile profile) {
		Instant since = Instant.now().minus(Duration.ofDays(properties.rankWindowDays()));
		String promptHash = ranker.promptHash(profile);
		Set<Long> ranked = new HashSet<>(matches.rankedJobIds(profile.getId(), promptHash));
		ranked.addAll(rankFailures.givenUp(profile.getId(), promptHash));
		List<Job> candidates = jobs.findByFetchedAtAfterOrderByIdDesc(since).stream()
				.filter(job -> !ranked.contains(job.getId()))
				.limit(properties.maxRankedPerRun())
				.toList();
		return candidates.isEmpty() ? RankReport.none() : ranker.rank(profile, candidates, callGuard(run, profile));
	}

	/** SUCCEEDED when no source failed, PARTIAL when some did and some worked, FAILED when none worked. */
	private static void finish(Run run, List<SourceStatus> statuses, String rankingError) {
		boolean anyFailed = statuses.contains(SourceStatus.FAILED);
		boolean anySucceeded = statuses.contains(SourceStatus.SUCCEEDED);
		if (anyFailed && !anySucceeded) {
			run.finish(RunStatus.FAILED, rankingError);
		}
		else if (anyFailed || rankingError != null) {
			run.finish(RunStatus.PARTIAL, rankingError);
		}
		else {
			run.finish(RunStatus.SUCCEEDED, null);
		}
	}
}
