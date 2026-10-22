package com.jobsearch.joblist.source.wttj;

import com.jobsearch.joblist.source.QueryKeys;
import com.jobsearch.joblist.source.Region;
import com.jobsearch.joblist.source.SourceQuery;
import com.jobsearch.joblist.source.ApifyFetcher;
import com.jobsearch.joblist.source.JobSource;
import com.jobsearch.joblist.source.SourceName;
import com.jobsearch.joblist.source.SourceResult;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Welcome to the Jungle, through the {@code clearpath/welcome-to-the-jungle-jobs-api} actor. */
@Component
class WttjSource implements JobSource {

	private static final String ACTOR = "clearpath/welcome-to-the-jungle-jobs-api";
	/** The only freshness windows (in days) the actor accepts. */
	private static final List<Integer> DATE_POSTED_DAYS = List.of(1, 3, 7, 14, 30);

	private final ApifyFetcher fetcher;
	private final WttjMapper mapper;
	private final ObjectMapper json;

	WttjSource(ApifyFetcher fetcher, WttjMapper mapper, ObjectMapper json) {
		this.fetcher = fetcher;
		this.mapper = mapper;
		this.json = json;
	}

	@Override
	public SourceName name() {
		return SourceName.WTTJ;
	}

	@Override
	public boolean supports(SourceQuery query) {
		return true;
	}

	@Override
	public String queryKey(SourceQuery query) {
		// only the first role and the first region reach the actor (see input)
		return QueryKeys.ofFirst(name(), query.roles(), query.regions().stream().map(this::place).toList());
	}

	private String place(Region region) {
		return region.country() + "/" + (region.city() == null ? "" : region.city());
	}

	@Override
	public SourceResult fetch(SourceQuery query) {
		return fetcher.fetch(name(), ACTOR, input(query), query.maxItems(), mapper::map);
	}

	/** Details are on because the description only comes with them. */
	private ObjectNode input(SourceQuery query) {
		ObjectNode input = json.createObjectNode();
		// The actor takes a single query and we do not invent a way to combine phrases, so only the first role is used.
		if (!query.roles().isEmpty()) {
			input.put("query", query.roles().getFirst());
		}
		putPlace(input, query);
		input.put("websiteCountry", "en");
		input.put("sortBy", "date");
		input.put("datePosted", String.valueOf(datePostedDays(query.lookbackDays())));
		input.put("includeDetails", true);
		input.put("maxItems", query.maxItems());
		return input;
	}

	private static void putPlace(ObjectNode input, SourceQuery query) {
		if (query.regions().isEmpty()) {
			return;
		}
		Region region = query.regions().getFirst();
		if (region.city() != null) {
			input.put("location", region.city());
		}
		input.put("countryCode", region.country());
	}

	private static int datePostedDays(int lookbackDays) {
		return DATE_POSTED_DAYS.stream()
				.filter(days -> days >= lookbackDays)
				.findFirst()
				.orElse(DATE_POSTED_DAYS.getLast());
	}
}
