package com.jobsearch.core_api.jobsearch;

import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.common.BadRequestException;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.common.ServiceUnavailableException;
import com.jobsearch.core_api.config.AppProperties;
import com.jobsearch.core_api.jobsearch.JobListClient.JobListProfile;
import com.jobsearch.core_api.jobsearch.JobListClient.MatchQuery;
import com.jobsearch.core_api.jobsearch.JobListClient.ProfileBody;
import com.jobsearch.core_api.jobsearch.JobSearchDtos.MatchPage;
import com.jobsearch.core_api.jobsearch.JobSearchDtos.MatchResponse;
import com.jobsearch.core_api.jobsearch.JobSearchDtos.ProfileRequest;
import com.jobsearch.core_api.jobsearch.JobSearchDtos.ProfileResponse;
import com.jobsearch.core_api.jobsearch.JobSearchDtos.RegionDto;
import com.jobsearch.core_api.jobsearch.JobSearchDtos.RunResponse;
import com.jobsearch.core_api.jobsearch.JobSearchDtos.State;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The signed-in user's job search on top of job-list. {@code ownerRef} is the user id and a user has exactly one
 * profile. job-list scopes by client (all of core-api), not by owner, so every id-based call first proves that the
 * profile (or match) belongs to this user.
 */
@Service
public class JobSearchService {

	private static final Logger log = LoggerFactory.getLogger(JobSearchService.class);
	static final String PROFILE_NAME = "Search";
	static final int DEFAULT_LIMIT = 20;
	static final int MAX_LIMIT = 50;
	/** Pages of {@value #MAX_LIMIT} walked per state when looking for a match id: 500 matches of each state. */
	private static final int OWNERSHIP_MAX_PAGES = 10;

	private final AtomicBoolean countFailing = new AtomicBoolean();
	private final JobListClient client;
	private final CurrentUserService currentUser;
	private final AppProperties properties;

	public JobSearchService(JobListClient client, CurrentUserService currentUser, AppProperties properties) {
		this.client = client;
		this.currentUser = currentUser;
		this.properties = properties;
	}

	public State state() {
		if (!configured()) {
			return new State(false, null, null, 0);
		}
		Optional<JobListProfile> profile = findProfile();
		RunResponse latest = profile.flatMap(p -> latestRun(p.id())).orElse(null);
		Integer unranked = profile.isPresent() ? unranked(profile.get().id()) : Integer.valueOf(0);
		return new State(true, profile.map(JobSearchService::toResponse).orElse(null), latest, unranked);
	}

	public ProfileResponse saveProfile(ProfileRequest request) {
		requireConfigured();
		String ownerRef = ownerRef();
		ProfileBody body = new ProfileBody(ownerRef, PROFILE_NAME, request.expectations().strip(),
				request.roles().stream().map(String::strip).toList(), regions(request.regions()),
				request.minScore() == null ? 0 : request.minScore());
		Optional<JobListProfile> existing = findProfile();
		if (existing.isPresent()) {
			return toResponse(client.updateProfile(existing.get().id(), body));
		}
		try {
			return toResponse(client.createProfile(body));
		}
		catch (BadRequestException ex) {
			// A parallel first PUT created the profile between our lookup and create (job-list's unique name): update it.
			Optional<JobListProfile> raced = findProfile();
			if (raced.isEmpty()) {
				throw ex;
			}
			return toResponse(client.updateProfile(raced.get().id(), body));
		}
	}

	public RunResponse startRun() {
		requireConfigured();
		JobListProfile profile = findProfile().orElseThrow(() -> new ConflictException("Save a search profile first"));
		return client.startRun(profile.id());
	}

	public RunResponse startRankRun() {
		requireConfigured();
		JobListProfile profile = findProfile().orElseThrow(() -> new ConflictException("Save a search profile first"));
		return client.startRankRun(profile.id());
	}

	/** A counter must never break the page: a failing count is null (unknown), logged once per outage. */
	private Integer unranked(long profileId) {
		try {
			int count = Math.max(0, client.unranked(profileId));
			countFailing.set(false);
			return count;
		}
		catch (RuntimeException ex) {
			if (countFailing.getAndSet(true)) {
				log.debug("Unranked count still failing for profile {}: {}", profileId, ex.getClass().getSimpleName());
			}
			else {
				log.warn("Could not count unranked postings of profile {}: {}", profileId, ex.getClass().getSimpleName());
			}
			return null;
		}
	}

	public MatchPage matches(Integer minScore, Boolean delivered, Integer limit, String cursor) {
		requireConfigured();
		Optional<JobListProfile> profile = findProfile();
		if (profile.isEmpty()) {
			return new MatchPage(List.of(), null);
		}
		Integer score = minScore == null ? null : Math.clamp(minScore, 0, 100);
		int size = limit == null ? DEFAULT_LIMIT : Math.clamp(limit, 1, MAX_LIMIT);
		return client.matches(profile.get().id(), new MatchQuery(score, delivered, size, cursor));
	}

	/**
	 * Marks a match seen. job-list's ack checks the client only, so the match must first be found among this
	 * user's own matches: job-list has no "get match" call, so the (bounded) pages of the user's profile are walked,
	 * unseen first (the normal case), then seen (acking again is harmless). Anything else, including a foreign id, is 404.
	 * Matches beyond the bound cannot be marked seen through the app. The proper fix is an owner-scoped ack/get in
	 * job-list (follow-up).
	 */
	public void markSeen(long matchId) {
		requireConfigured();
		JobListProfile profile = findProfile().orElseThrow(() -> new NotFoundException("Match not found"));
		if (!ownsMatch(profile.id(), matchId, false) && !ownsMatch(profile.id(), matchId, true)) {
			throw new NotFoundException("Match not found");
		}
		client.ack(matchId);
	}

	private boolean ownsMatch(long profileId, long matchId, boolean delivered) {
		String cursor = null;
		for (int page = 0; page < OWNERSHIP_MAX_PAGES; page++) {
			MatchPage result = client.matches(profileId, new MatchQuery(0, delivered, MAX_LIMIT, cursor));
			if (result.items() != null && result.items().stream().map(MatchResponse::id).anyMatch(id -> id == matchId)) {
				return true;
			}
			cursor = result.nextCursor();
			if (cursor == null) {
				return false;
			}
		}
		return false;
	}

	private boolean configured() {
		return properties.getJobList().isConfigured();
	}

	private void requireConfigured() {
		if (!configured()) {
			throw new ServiceUnavailableException("job search is not configured", null);
		}
	}

	private String ownerRef() {
		return String.valueOf(currentUser.requireUserId());
	}

	/** The user's one profile; the owner is compared again here in case job-list's filter ever matched loosely. */
	private Optional<JobListProfile> findProfile() {
		String ownerRef = ownerRef();
		return client.profiles(ownerRef).stream()
				.filter(p -> ownerRef.equals(p.ownerRef()))
				.min(Comparator.comparingLong(JobListProfile::id));
	}

	private Optional<RunResponse> latestRun(long profileId) {
		return client.runs(profileId).stream().max(Comparator.comparingLong(RunResponse::id));
	}

	private static List<RegionDto> regions(List<RegionDto> requested) {
		if (requested == null) {
			return List.of();
		}
		return requested.stream().map(r -> new RegionDto(
				r.country().toUpperCase(Locale.ROOT),
				r.city() == null || r.city().isBlank() ? null : r.city().strip())).distinct().toList();
	}

	private static ProfileResponse toResponse(JobListProfile p) {
		return new ProfileResponse(p.id(), p.name(), p.expectations(), p.roles(),
				p.regions() == null ? List.of() : p.regions(), p.sources(), p.enabledSources(), p.minScore());
	}
}
