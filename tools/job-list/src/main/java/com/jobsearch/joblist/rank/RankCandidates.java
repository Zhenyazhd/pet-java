package com.jobsearch.joblist.rank;

import com.jobsearch.joblist.config.JobListProperties;
import com.jobsearch.joblist.job.Job;
import com.jobsearch.joblist.job.JobRepository;
import com.jobsearch.joblist.job.PostingAge;
import com.jobsearch.joblist.profile.SearchProfile;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The jobs to rank for a profile: stored within the rank window and not older than the posting age limit (the stricter
 * wins), not yet ranked for this profile with the current prompt and expectations, and not given up on; newest first.
 * One rule for the ranking phase of every run and for the count of unranked jobs.
 */
@Component
public class RankCandidates {

	private final JobRepository jobs;
	private final MatchRepository matches;
	private final RankFailures rankFailures;
	private final JobRanker ranker;
	private final JobListProperties properties;
	private final JdbcTemplate jdbc;

	public RankCandidates(JobRepository jobs, MatchRepository matches, RankFailures rankFailures, JobRanker ranker, JobListProperties properties, JdbcTemplate jdbc) {
		this.jobs = jobs;
		this.matches = matches;
		this.rankFailures = rankFailures;
		this.ranker = ranker;
		this.properties = properties;
		this.jdbc = jdbc;
	}

	/** All candidates, not capped. */
	public List<Job> find(SearchProfile profile, Instant now) {
		Instant since = now.minus(Duration.ofDays(properties.rankWindowDays()));
		Instant postedAfter = PostingAge.cutoff(now, properties.maxPostingAgeDays());
		String promptHash = ranker.promptHash(profile);
		Set<Long> ranked = new HashSet<>(matches.rankedJobIds(profile.getId(), promptHash));
		ranked.addAll(rankFailures.givenUp(profile.getId(), promptHash));
		return jobs.findCandidates(since, postedAfter).stream().filter(job -> !ranked.contains(job.getId())).toList();
	}

	/** The number of candidates as one SQL count (no entities, no descriptions); it must agree with {@link #find}. */
	public int count(SearchProfile profile) {
		Instant now = Instant.now();
		String promptHash = ranker.promptHash(profile);
		Integer count = jdbc.queryForObject("""
				select count(*) from job j
				where j.fetched_at > ? and coalesce(j.posted_at, j.fetched_at) >= ?
				  and not exists (select 1 from match m where m.job_id = j.id and m.profile_id = ? and m.prompt_hash = ?)
				  and not exists (select 1 from rank_failure f where f.job_id = j.id and f.profile_id = ? and f.prompt_hash = ? and f.failures >= ?)
				""", Integer.class,
				Timestamp.from(now.minus(Duration.ofDays(properties.rankWindowDays()))),
				Timestamp.from(PostingAge.cutoff(now, properties.maxPostingAgeDays())),
				profile.getId(), promptHash, profile.getId(), promptHash, RankFailures.GIVE_UP_AFTER);
		return count == null ? 0 : count;
	}
}
