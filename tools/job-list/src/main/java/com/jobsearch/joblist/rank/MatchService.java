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

	private final MatchRepository matches;
	private final SearchProfileService profiles;
	private final int maxPostingAgeDays;

	public MatchService(MatchRepository matches, SearchProfileService profiles, JobListProperties properties) {
		this.matches = matches;
		this.profiles = profiles;
		this.maxPostingAgeDays = properties.maxPostingAgeDays();
	}

	/** Query options of one page; null values mean "use the default" (matches not yet delivered, the profile's minimum score). */
	public record Query(Integer minScore, Boolean delivered, String since, Integer limit, String cursor) {
	}

	@Transactional(readOnly = true)
	public MatchPage page(String client, long profileId, Query query) {
		SearchProfile profile = profiles.owned(client, profileId);
		int limit = limit(query.limit());
		long[] cursor = cursor(query.cursor());
		List<Match> found = matches.page(
				profileId,
				query.minScore() == null ? profile.getMinScore() : query.minScore(),
				since(query.since()),
				PostingAge.cutoff(Instant.now(), maxPostingAgeDays),
				Boolean.TRUE.equals(query.delivered()),
				(int) cursor[0],
				cursor[1],
				PageRequest.ofSize(limit));
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
