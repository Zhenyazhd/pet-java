package com.jobsearch.joblist.rank;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The jobs the model could not rank for a profile and prompt. After {@link #GIVE_UP_AFTER} failures a job is not offered
 * again for that prompt: a posting that makes the model break the format would otherwise be paid for on every run and
 * would take a place in the per-run cap for ever.
 */
@Component
public class RankFailures {

	static final int GIVE_UP_AFTER = 2;

	private final JdbcTemplate jdbc;

	public RankFailures(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Set<Long> givenUp(long profileId, String promptHash) {
		return new HashSet<>(jdbc.queryForList(
				"select job_id from rank_failure where profile_id = ? and prompt_hash = ? and failures >= ?",
				Long.class, profileId, promptHash, GIVE_UP_AFTER));
	}

	void record(long profileId, String promptHash, Collection<Long> jobIds) {
		for (Long jobId : jobIds) {
			jdbc.update("""
					insert into rank_failure (profile_id, job_id, prompt_hash, failures) values (?, ?, ?, 1)
					on conflict (profile_id, job_id, prompt_hash) do update set failures = rank_failure.failures + 1
					""", profileId, jobId, promptHash);
		}
	}
}
