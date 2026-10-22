package com.jobsearch.joblist.source.hellowork;

import java.util.List;
import com.jobsearch.joblist.source.QueryKeys;
import com.jobsearch.joblist.source.Region;
import com.jobsearch.joblist.source.SourceQuery;
import com.jobsearch.joblist.source.ApifyFetcher;
import com.jobsearch.joblist.source.JobSource;
import com.jobsearch.joblist.source.SourceName;
import com.jobsearch.joblist.source.SourceResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** HelloWork, France's generalist job board, through the {@code dltik/hellowork-scraper} actor. */
@Component
class HelloWorkSource implements JobSource {

	private static final String ACTOR = "dltik/hellowork-scraper";

	private final ApifyFetcher fetcher;
	private final HelloWorkMapper mapper;
	private final ObjectMapper json;

	HelloWorkSource(ApifyFetcher fetcher, HelloWorkMapper mapper, ObjectMapper json) {
		this.fetcher = fetcher;
		this.mapper = mapper;
		this.json = json;
	}

	@Override
	public SourceName name() {
		return SourceName.HELLOWORK;
	}

	@Override
	public boolean supports(SourceQuery query) {
		return query.regions().stream().anyMatch(region -> "FR".equals(region.country()));
	}

	@Override
	public String queryKey(SourceQuery query) {
		// every role is a keyword; of the regions only the location that location() picks reaches the actor
		return QueryKeys.ofAll(name(), query.roles(), List.of(location(query)));
	}

	@Override
	public SourceResult fetch(SourceQuery query) {
		return fetcher.fetch(name(), ACTOR, input(query), query.maxItems(), mapper::map);
	}

	/**
	 * Details are on because the description comes with them; the actor's own AI scoring and company lookup stay
	 * off, because each is paid per job and the ranking is done here.
	 */
	private ObjectNode input(SourceQuery query) {
		ObjectNode input = json.createObjectNode();
		query.roles().forEach(input.putArray("keywords")::add);
		input.put("location", location(query));
		input.put("enrichDetails", true);
		input.put("enrichCompany", false);
		input.put("analyzeJobs", false);
		input.put("skipReposts", true);
		input.put("maxResults", query.maxItems());
		return input;
	}

	/** The first French city asked for, else the whole country. */
	private static String location(SourceQuery query) {
		return query.regions().stream()
				.filter(region -> "FR".equals(region.country()) && region.city() != null)
				.map(Region::city)
				.findFirst()
				.orElse("France");
	}
}
