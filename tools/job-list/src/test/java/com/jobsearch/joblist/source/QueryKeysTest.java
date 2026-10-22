package com.jobsearch.joblist.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.jobsearch.joblist.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Two requests are the same paid call exactly when the source would send the same thing to Apify. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class QueryKeysTest {

	@Autowired
	private List<JobSource> sourceList;

	private JobSource source(SourceName name) {
		Map<SourceName, JobSource> byName = sourceList.stream().collect(Collectors.toMap(JobSource::name, Function.identity()));
		return byName.get(name);
	}

	private static SourceQuery query(List<String> roles, Region... regions) {
		SourceQuery query = new SourceQuery();
		query.setRoles(roles);
		query.setRegions(List.of(regions));
		return query;
	}

	@Test
	void caseAndSpacesDoNotMakeAnotherCall() {
		for (SourceName name : SourceName.values()) {
			assertEquals(source(name).queryKey(query(List.of("python developer"), new Region("FR", "Paris"))),
					source(name).queryKey(query(List.of("  Python   Developer "), new Region("FR", "paris"))), name.toString());
		}
	}

	@Test
	void sourcesThatSendOnlyTheFirstRoleDoNotCareAboutTheOthers() {
		for (SourceName name : List.of(SourceName.WTTJ, SourceName.LINKEDIN)) {
			assertEquals(source(name).queryKey(query(List.of("python", "java"), new Region("FR", null))),
					source(name).queryKey(query(List.of("python", "go"), new Region("FR", null))), name + ": only the first role is sent");
			assertNotEquals(source(name).queryKey(query(List.of("python", "java"), new Region("FR", null))),
					source(name).queryKey(query(List.of("java", "python"), new Region("FR", null))), name + ": another first role is another search");
		}
	}

	@Test
	void sourcesThatSendEveryRoleSeeADifferentSearchWhenARoleChanges() {
		for (SourceName name : List.of(SourceName.HELLOWORK, SourceName.GREENHOUSE)) {
			assertNotEquals(source(name).queryKey(query(List.of("python", "java"), new Region("FR", null))),
					source(name).queryKey(query(List.of("python", "go"), new Region("FR", null))), name.toString());
			assertEquals(source(name).queryKey(query(List.of("python", "java"), new Region("FR", null))),
					source(name).queryKey(query(List.of("java", "python"), new Region("FR", null))), name + ": the order does not matter");
		}
	}

	@Test
	void helloWorkSendsOnlyOneFrenchPlaceSoTheOtherRegionsAreNotPartOfTheKey() {
		JobSource helloWork = source(SourceName.HELLOWORK);

		assertEquals(helloWork.queryKey(query(List.of("python"), new Region("FR", "Paris"), new Region("DE", "Berlin"))),
				helloWork.queryKey(query(List.of("python"), new Region("FR", "Paris"))));
		assertNotEquals(helloWork.queryKey(query(List.of("python"), new Region("FR", "Paris"))),
				helloWork.queryKey(query(List.of("python"), new Region("FR", "Lyon"))));
	}

	@Test
	void aRoleThatContainsTheJoinCharacterIsNotTwoRoles() {
		for (SourceName name : List.of(SourceName.HELLOWORK, SourceName.GREENHOUSE)) {
			assertNotEquals(source(name).queryKey(query(List.of("a|b"), new Region("FR", null))),
					source(name).queryKey(query(List.of("a", "b"), new Region("FR", null))), name.toString());
		}
	}

	@Test
	void theKeysOfTwoSourcesDiffer() {
		SourceQuery same = query(List.of("python"), new Region("FR", "Paris"));

		assertNotEquals(source(SourceName.WTTJ).queryKey(same), source(SourceName.LINKEDIN).queryKey(same));
	}
}
