package com.jobsearch.joblist.source.linkedin;

import com.jobsearch.joblist.source.QueryKeys;
import com.jobsearch.joblist.source.Region;
import com.jobsearch.joblist.source.SourceQuery;
import com.jobsearch.joblist.source.ApifyFetcher;
import com.jobsearch.joblist.source.JobSource;
import com.jobsearch.joblist.source.SourceName;
import com.jobsearch.joblist.source.SourceResult;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** LinkedIn Jobs through the {@code curious_coder/linkedin-jobs-scraper} actor, given a search URL. */
@Component
class LinkedInSource implements JobSource {

	private static final String ACTOR = "curious_coder/linkedin-jobs-scraper";
	private static final long SECONDS_PER_DAY = 86_400;

	private final ApifyFetcher fetcher;
	private final LinkedInMapper mapper;
	private final ObjectMapper json;

	LinkedInSource(ApifyFetcher fetcher, LinkedInMapper mapper, ObjectMapper json) {
		this.fetcher = fetcher;
		this.mapper = mapper;
		this.json = json;
	}

	@Override
	public SourceName name() {
		return SourceName.LINKEDIN;
	}

	@Override
	public boolean supports(SourceQuery query) {
		return !query.roles().isEmpty() && !query.regions().isEmpty();
	}

	@Override
	public String queryKey(SourceQuery query) {
		// the search URL holds the first role and the first region only
		return QueryKeys.ofFirst(name(), query.roles(), query.regions().stream().map(Region::place).toList());
	}

	@Override
	public SourceResult fetch(SourceQuery query) {
		String country = query.regions().getFirst().country();
		return fetcher.fetch(name(), ACTOR, input(query), query.maxItems(), item -> mapper.map(item, country));
	}

	private ObjectNode input(SourceQuery query) {
		ObjectNode input = json.createObjectNode();
		input.putArray("urls").add(searchUrl(query));
		input.put("count", query.maxItems());
		return input;
	}

	/** The actor reads the search from a LinkedIn URL; the first role and the first region are used. */
	private static String searchUrl(SourceQuery query) {
		return UriComponentsBuilder.fromUriString("https://www.linkedin.com/jobs/search/")
				.queryParam("keywords", query.roles().getFirst())
				.queryParam("location", query.regions().getFirst().place())
				.queryParam("f_TPR", "r" + query.lookbackDays() * SECONDS_PER_DAY)
				.build()
				.encode()
				.toUriString();
	}
}
