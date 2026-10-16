package com.jobsearch.joblist.source.greenhouse;

import com.jobsearch.joblist.source.QueryKeys;
import com.jobsearch.joblist.source.Region;
import com.jobsearch.joblist.source.SourceQuery;
import com.jobsearch.joblist.source.ApifyFetcher;
import com.jobsearch.joblist.source.JobSource;
import com.jobsearch.joblist.source.SourceName;
import com.jobsearch.joblist.source.SourceResult;
import java.util.Locale;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Greenhouse-hosted job boards, through the {@code fantastic-jobs/greenhouse-jobs-api} actor. */
@Component
class GreenhouseSource implements JobSource {

	private static final String ACTOR = "fantastic-jobs/greenhouse-jobs-api";
	private static final String MAX_JOBS_KEY = "maxJobs";
	/** The actor bills for at least this many jobs, so asking for fewer would save nothing. */
	private static final int MIN_BILLED_JOBS = 200;

	private final ApifyFetcher fetcher;
	private final GreenhouseMapper mapper;
	private final ObjectMapper json;

	GreenhouseSource(ApifyFetcher fetcher, GreenhouseMapper mapper, ObjectMapper json) {
		this.fetcher = fetcher;
		this.mapper = mapper;
		this.json = json;
	}

	@Override
	public SourceName name() {
		return SourceName.GREENHOUSE;
	}

	@Override
	public boolean supports(SourceQuery query) {
		return true;
	}

	@Override
	public String queryKey(SourceQuery query) {
		return QueryKeys.ofAll(name(), query.roles(), query.regions().stream().map(Region::place).toList());
	}

	@Override
	public SourceResult fetch(SourceQuery query) {
		int maxJobs = maxJobs(query);
		return fetcher.fetch(name(), ACTOR, input(query, maxJobs), maxJobs, mapper::map);
	}

	private ObjectNode input(SourceQuery query, int maxJobs) {
		ObjectNode input = json.createObjectNode();
		query.roles().forEach(input.putArray("titleSearch")::add);
		query.regions().forEach(region -> input.withArray("locationSearch").add(region.place()));
		input.put("descriptionType", "text");
		input.put("removeAgency", true);
		input.put(MAX_JOBS_KEY, maxJobs);
		return input;
	}

	private static int maxJobs(SourceQuery query) {
		return Math.max(MIN_BILLED_JOBS, query.maxItems());
	}

	/** The actor wants English place names, not codes. */
}
