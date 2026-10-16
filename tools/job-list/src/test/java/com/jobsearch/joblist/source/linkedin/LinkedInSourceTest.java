package com.jobsearch.joblist.source.linkedin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.jobsearch.joblist.TestProperties;
import com.jobsearch.joblist.source.Region;
import com.jobsearch.joblist.source.SourceQuery;
import com.jobsearch.joblist.source.ApifyFetcher;
import com.jobsearch.joblist.source.SourceName;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class LinkedInSourceTest {

	private final ApifyFetcher fetcher = mock(ApifyFetcher.class);
	private final LinkedInSource source = new LinkedInSource(fetcher, new LinkedInMapper(TestProperties.defaults()), new ObjectMapper());

	@Test
	void needsARoleAndARegion() {
		assertFalse(source.supports(new SourceQuery()));
		assertTrue(source.supports(query()));
	}

	@Test
	void sendsASearchUrlWithTheFirstRoleThePlaceAndTheWindow() {
		source.fetch(query());

		ArgumentCaptor<JsonNode> input = ArgumentCaptor.forClass(JsonNode.class);
		verify(fetcher).fetch(eq(SourceName.LINKEDIN), eq("curious_coder/linkedin-jobs-scraper"), input.capture(), eq(50), any());
		assertEquals(50, input.getValue().get("count").intValue());
		assertEquals("https://www.linkedin.com/jobs/search/?keywords=python%20developer&location=Paris&f_TPR=r259200",
				input.getValue().get("urls").get(0).asString());
	}

	private static SourceQuery query() {
		SourceQuery query = new SourceQuery();
		query.setRoles(List.of("python developer", "backend engineer"));
		query.setRegions(List.of(new Region("FR", "Paris")));
		query.setWindow(3, 50);
		return query;
	}
}
