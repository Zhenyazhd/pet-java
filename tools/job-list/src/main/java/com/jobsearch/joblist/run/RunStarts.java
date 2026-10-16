package com.jobsearch.joblist.run;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The runs each client and owner started recently; it outlives the profiles, so deleting a profile does not reset a limit. */
@Component
public class RunStarts {

	private final JdbcTemplate jdbc;

	public RunStarts(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public long byOwnerSince(String client, String ownerRef, Instant since) {
		Long count = jdbc.queryForObject("select count(*) from client_run_start where client = ? and owner_ref = ? and created_at >= ?",
				Long.class, client, ownerRef, Timestamp.from(since));
		return count == null ? 0 : count;
	}

	public long byClientSince(String client, Instant since) {
		Long count = jdbc.queryForObject("select count(*) from client_run_start where client = ? and created_at >= ?",
				Long.class, client, Timestamp.from(since));
		return count == null ? 0 : count;
	}

	public void record(String client, String ownerRef) {
		jdbc.update("insert into client_run_start (client, owner_ref, created_at) values (?, ?, now())", client, ownerRef);
	}

	public int deleteBefore(Instant before) {
		return jdbc.update("delete from client_run_start where created_at < ?", Timestamp.from(before));
	}
}
