package com.jobsearch.joblist.source.wttj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.jobsearch.joblist.source.Region;
import com.jobsearch.joblist.source.SourceQuery;
import com.jobsearch.joblist.source.ApifyFetcher;
import com.jobsearch.joblist.source.SourceName;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class WttjSourceTest {

	private final ApifyFetcher fetcher = mock(ApifyFetcher.class);
	private final WttjSource source = new WttjSource(fetcher, mock(WttjMapper.class), JsonMapper.builder().build());

	@Test
	void supportsEveryQuery() {
		assertThat(source.supports(new SourceQuery())).isTrue();
	}

	@Test
	void sendsTheFirstRoleThePlaceAndTheSmallestAcceptedWindow() {
		SourceQuery query = new SourceQuery();
		query.setRoles(List.of("Java developer", "Backend engineer"));
		query.setRegions(List.of(new Region("FR", "Lyon")));
		query.setWindow(5, 50);

		JsonNode input = fetchedInput(query);

		assertThat(input.get("query").asString()).isEqualTo("Java developer");
		assertThat(input.get("location").asString()).isEqualTo("Lyon");
		assertThat(input.get("countryCode").asString()).isEqualTo("FR");
		assertThat(input.get("datePosted").asString()).isEqualTo("7");
		assertThat(input.get("sortBy").asString()).isEqualTo("date");
		assertThat(input.get("includeDetails").asBoolean()).isTrue();
		assertThat(input.get("maxItems").asInt()).isEqualTo(50);
	}

	@Test
	void capsTheWindowAndSendsOnlyTheCountryWithoutACity() {
		SourceQuery query = new SourceQuery();
		query.setRegions(List.of(new Region("DE", null)));
		query.setWindow(90, 10);

		JsonNode input = fetchedInput(query);

		assertThat(input.has("query")).isFalse();
		assertThat(input.has("location")).isFalse();
		assertThat(input.get("countryCode").asString()).isEqualTo("DE");
		assertThat(input.get("datePosted").asString()).isEqualTo("30");
	}

	private JsonNode fetchedInput(SourceQuery query) {
		source.fetch(query);
		ArgumentCaptor<JsonNode> input = ArgumentCaptor.forClass(JsonNode.class);
		verify(fetcher).fetch(eq(SourceName.WTTJ), eq("clearpath/welcome-to-the-jungle-jobs-api"), input.capture(),
				eq(query.maxItems()), any());
		return input.getValue();
	}
}
