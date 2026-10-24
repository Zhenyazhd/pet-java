package com.jobsearch.joblist.rank;

import com.jobsearch.joblist.common.BadRequestException;
import com.jobsearch.joblist.common.NotFoundException;
import com.jobsearch.joblist.config.JobListProperties;
import com.jobsearch.joblist.job.Job;
import com.jobsearch.joblist.job.PostingAge;
import com.jobsearch.joblist.profile.SearchProfile;
import com.jobsearch.joblist.profile.SearchProfileService;
import com.jobsearch.joblist.rank.MatchDtos.JobSummary;
import com.jobsearch.joblist.rank.MatchDtos.MatchPage;
import com.jobsearch.joblist.rank.MatchDtos.MatchResponse;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading ranked matches and marking them delivered, for the calling client's profiles. A match whose job is older than
 * the posting age limit at the time of the request is not returned (it ages out of the list by itself).
 */
@Service
@Transactional
public class MatchService {

	private static final int DEFAULT_LIMIT = 20;
	private static final int MAX_LIMIT = 100;
	private static final int SNIPPET_CHARS = 1500;
	/** Above every real score, so the first page starts at the top. */
	private static final int TOP_OF_RANGE = 101;
	/** After every real posting date, so the first page of the newest-first order starts at the top. */
	private static final Instant END_OF_TIME = Instant.parse("9999-12-31T00:00:00Z");
	private static final String POSTED_PREFIX = "p:";

	private final MatchRepository matches;
	private final SearchProfileService profiles;
	private final int maxPostingAgeDays;

	public MatchService(MatchRepository matches, SearchProfileService profiles, JobListProperties properties) {
		this.matches = matches;
		this.profiles = profiles;
		this.maxPostingAgeDays = properties.maxPostingAgeDays();
	}

	/**
	 * Query options of one page; null values mean "use the default" (matches not yet delivered, the profile's minimum
	 * score, best score first). {@code sort} is "score" or "posted" (newest posting first).
	 */
	public record Query(Integer minScore, Boolean delivered, String since, Integer limit, String cursor, String sort) {

		public Query(Integer minScore, Boolean delivered, String since, Integer limit, String cursor) {
			this(minScore, delivered, since, limit, cursor, null);
		}
	}

	@Transactional(readOnly = true)
	public MatchPage page(String client, long profileId, Query query) {
		SearchProfile profile = profiles.owned(client, profileId);
		int limit = limit(query.limit());
		int minScore = query.minScore() == null ? profile.getMinScore() : query.minScore();
		Instant since = since(query.since());
		Instant postedAfter = PostingAge.cutoff(Instant.now(), maxPostingAgeDays);
		boolean delivered = Boolean.TRUE.equals(query.delivered());
		if (newestFirst(query.sort())) {
			Object[] cursor = postedCursor(query.cursor());
			List<Match> found = matches.pageByPosted(profileId, minScore, since, postedAfter, delivered,
					(Instant) cursor[0], (long) cursor[1], PageRequest.ofSize(limit));
			String next = found.size() == limit
					? POSTED_PREFIX + ChronoUnit.MICROS.between(Instant.EPOCH, postedInstant(found.getLast())) + ":" + found.getLast().getId()
					: null;
			return new MatchPage(found.stream().map(MatchService::toResponse).toList(), next);
		}
		long[] cursor = cursor(query.cursor());
		List<Match> found = matches.page(profileId, minScore, since, postedAfter, delivered,
				(int) cursor[0], cursor[1], PageRequest.ofSize(limit));
		List<MatchResponse> items = found.stream().map(MatchService::toResponse).toList();
		String next = found.size() == limit ? found.getLast().getScore() + ":" + found.getLast().getId() : null;
		return new MatchPage(items, next);
	}

	public void markDelivered(String client, long matchId) {
		Match match = matches.findOwned(matchId, client).orElseThrow(() -> new NotFoundException("Match not found: " + matchId));
		if (match.getDeliveredAt() == null) {
			match.markDelivered();
		}
	}

	private static int limit(Integer requested) {
		if (requested == null) {
			return DEFAULT_LIMIT;
		}
		if (requested < 1 || requested > MAX_LIMIT) {
			throw new BadRequestException("limit must be between 1 and " + MAX_LIMIT);
		}
		return requested;
	}

	private static Instant since(String since) {
		try {
			return since == null ? Instant.EPOCH : Instant.parse(since);
		}
		catch (DateTimeParseException ex) {
			throw new BadRequestException("since must be an ISO-8601 instant, e.g. 2026-10-01T00:00:00Z");
		}
	}

	private static boolean newestFirst(String sort) {
		if (sort == null || sort.equals("score")) {
			return false;
		}
		if (sort.equals("posted")) {
			return true;
		}
		throw new BadRequestException("sort must be score or posted");
	}

	/** The instant the newest-first order and its cursor use: the posting date, or the fetch date when there is none. */
	private static Instant postedInstant(Match match) {
		Job job = match.getJob();
		return job.getPostedAt() != null ? job.getPostedAt() : job.getFetchedAt();
	}

	/** {instant, id} of the last item of the previous newest-first page (microseconds, as the database keeps them), or the top. */
	private static Object[] postedCursor(String cursor) {
		if (cursor == null) {
			return new Object[] { END_OF_TIME, Long.MAX_VALUE };
		}
		String[] parts = cursor.split(":");
		try {
			if (parts.length == 3 && parts[0].equals("p")) {
				return new Object[] { Instant.EPOCH.plus(Long.parseLong(parts[1]), ChronoUnit.MICROS), Long.parseLong(parts[2]) };
			}
		}
		catch (NumberFormatException | java.time.DateTimeException | ArithmeticException ex) {
			// falls through to the same message
		}
		throw new BadRequestException("cursor must be the nextCursor of a previous page");
	}

	/** {score, id} of the last item of the previous page, or the top of the range. */
	private static long[] cursor(String cursor) {
		if (cursor == null) {
			return new long[] { TOP_OF_RANGE, Long.MAX_VALUE };
		}
		String[] parts = cursor.split(":");
		try {
			if (parts.length == 2) {
				return new long[] { Integer.parseInt(parts[0]), Long.parseLong(parts[1]) };
			}
		}
		catch (NumberFormatException ex) {
			// falls through to the same message
		}
		throw new BadRequestException("cursor must be the nextCursor of a previous page");
	}

	private static MatchResponse toResponse(Match match) {
		Job job = match.getJob();
		JobSummary summary = new JobSummary(
				job.getId(),
				job.getSource().name(),
				job.getTitle(),
				job.getCompany(),
				job.locationText(),
				job.getRemote().name(),
				job.getContractType().name(),
				job.salaryText(),
				job.getUrl(),
				job.getPostedAt() == null ? null : job.getPostedAt().toString(),
				job.getSkills(),
				snippet(job.getDescriptionText()));
		return new MatchResponse(match.getId(), match.getScore(), match.getReason(), match.getRankedAt().toString(),
				match.getDeliveredAt() == null ? null : match.getDeliveredAt().toString(), summary);
	}

	private static String snippet(String description) {
		return description.length() <= SNIPPET_CHARS ? description : description.substring(0, SNIPPET_CHARS).strip() + "…";
	}
}
