package com.jobsearch.joblist.source.linkedin;

import com.jobsearch.joblist.source.QueryKeys;
import com.jobsearch.joblist.source.Region;
import com.jobsearch.joblist.source.SourceQuery;
import com.jobsearch.joblist.source.ApifyFetcher;
import com.jobsearch.joblist.source.JobSource;
import com.jobsearch.joblist.source.SourceName;
import com.jobsearch.joblist.source.SourceResult;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** LinkedIn Jobs through the {@code curious_coder/linkedin-jobs-scraper} actor, given a search URL. */
@Component
class LinkedInSource implements JobSource {

	private static final String ACTOR = "curious_coder/linkedin-jobs-scraper";
	private static final long SECONDS_PER_DAY = 86_400;
	/** shortcut: LinkedIn search URLs get long, so only this many roles are sent; raise it if the actor accepts longer ones. */
	private static final int MAX_ROLES = 10;

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
		// the search URL holds the roles (up to MAX_ROLES) and the first region only
		return QueryKeys.ofAll(name(), usedRoles(query),
				query.regions().stream().limit(1).map(Region::place).toList());
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

	private static List<String> usedRoles(SourceQuery query) {
		return query.roles().stream().limit(MAX_ROLES).toList();
	}

	/** One role is searched as typed; several become a boolean search: "a" OR "b" (quotes keep each title together). */
	private static String keywords(SourceQuery query) {
		List<String> roles = usedRoles(query);
		if (roles.size() == 1) {
			return roles.getFirst();
		}
		return roles.stream().map(role -> '"' + role.replace("\"", "") + '"').collect(Collectors.joining(" OR "));
	}

	/** The actor reads the search from a LinkedIn URL; all roles (see {@link #keywords}) and the first region are used. */
	private static String searchUrl(SourceQuery query) {
		return UriComponentsBuilder.fromUriString("https://www.linkedin.com/jobs/search/")
				.queryParam("keywords", keywords(query))
				.queryParam("location", query.regions().getFirst().place())
				.queryParam("f_TPR", "r" + query.lookbackDays() * SECONDS_PER_DAY)
				.build()
				.encode()
				.toUriString();
	}
}
