package com.jobsearch.joblist.source.greenhouse;

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

class GreenhouseSourceTest {

	private static final String ACTOR = "fantastic-jobs/greenhouse-jobs-api";

	private final ApifyFetcher fetcher = mock(ApifyFetcher.class);
	private final GreenhouseSource source = new GreenhouseSource(
			fetcher, mock(GreenhouseMapper.class), JsonMapper.builder().build());

	@Test
	void supportsEveryQuery() {
		assertThat(source.supports(new SourceQuery())).isTrue();
	}

	@Test
	void sendsAllRolesAndEnglishPlaceNames() {
		SourceQuery query = new SourceQuery();
		query.setRoles(List.of("Java developer", "Backend engineer"));
		query.setRegions(List.of(new Region("FR", "Paris"), new Region("DE", null)));
		query.setWindow(7, 500);

		JsonNode input = fetchedInput(query, 500);

		assertThat(input.get("titleSearch")).extracting(JsonNode::asString)
				.containsExactly("Java developer", "Backend engineer");
		assertThat(input.get("locationSearch")).extracting(JsonNode::asString).containsExactly("Paris", "Germany");
		assertThat(input.get("descriptionType").asString()).isEqualTo("text");
		assertThat(input.get("removeAgency").asBoolean()).isTrue();
		assertThat(input.get("maxJobs").asInt()).isEqualTo(500);
	}

	@Test
	void asksForAtLeastTheMinimumBilledJobs() {
		SourceQuery query = new SourceQuery();
		query.setWindow(7, 50);

		JsonNode input = fetchedInput(query, 200);

		assertThat(input.get("maxJobs").asInt()).isEqualTo(200);
	}

	private JsonNode fetchedInput(SourceQuery query, int expectedMaxItems) {
		source.fetch(query);
		ArgumentCaptor<JsonNode> input = ArgumentCaptor.forClass(JsonNode.class);
		verify(fetcher).fetch(eq(SourceName.GREENHOUSE), eq(ACTOR), input.capture(), eq(expectedMaxItems), any());
		return input.getValue();
	}
}
