package com.jobsearch.joblist;

import com.jobsearch.joblist.job.ContractType;
import com.jobsearch.joblist.job.Job;
import com.jobsearch.joblist.job.Remote;
import com.jobsearch.joblist.source.SourceName;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Builders and cleanup shared by the integration tests. */
public final class TestData {

	private TestData() {
	}

	/** Tests share one database; this empties every table and restarts the ids. */
	public static void reset(JdbcTemplate jdbc) {
		jdbc.execute("TRUNCATE match, run_source, spend_ledger, client_run_start, rank_failure, run, job, search_profile RESTART IDENTITY CASCADE");
	}

	/** A French job at a company that exists nowhere else, so fingerprints never collide between tests. */
	public static Job job(SourceName source, String title) {
		String unique = UUID.randomUUID().toString();
		Job job = new Job();
		job.setSource(source);
		job.setExternalId(unique);
		job.setUrl("https://jobs.example.com/" + unique);
		job.setTitle(title);
		job.setCompany("Company " + unique);
		job.setDescriptionText("Build and run services in Python.");
		job.setCountries(List.of("FR"));
		job.setCities(List.of("Paris"));
		job.setRemote(Remote.HYBRID);
		job.setContractType(ContractType.FULL_TIME);
		job.setSkills(List.of("Python"));
		return job;
	}
}
