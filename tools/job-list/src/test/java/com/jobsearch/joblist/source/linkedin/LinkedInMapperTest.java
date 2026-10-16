package com.jobsearch.joblist.source.linkedin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jobsearch.joblist.TestProperties;
import com.jobsearch.joblist.job.ContractType;
import com.jobsearch.joblist.job.Job;
import com.jobsearch.joblist.job.Remote;
import com.jobsearch.joblist.source.SourceName;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class LinkedInMapperTest {

	private final LinkedInMapper mapper = new LinkedInMapper(TestProperties.defaults());
	private final List<JsonNode> items = fixture();

	@Test
	void completeItemBecomesAJobInTheSearchedCountry() {
		Job job = mapper.map(items.get(0), "FR").orElseThrow();

		assertEquals(SourceName.LINKEDIN, job.getSource());
		assertEquals("3692563200", job.getExternalId());
		assertEquals(List.of("FR"), job.getCountries());
		assertEquals(List.of("Paris"), job.getCities());
		assertEquals(Remote.HYBRID, job.getRemote());
		assertEquals(ContractType.FULL_TIME, job.getContractType());
		assertEquals(0, new BigDecimal("55000.00").compareTo(job.getSalaryMin()));
		assertEquals(0, new BigDecimal("70000.00").compareTo(job.getSalaryMax()));
		assertEquals("EUR", job.getSalaryCurrency());
		assertEquals(Instant.parse("2026-10-10T00:00:00Z"), job.getPostedAt());
	}

	@Test
	void areaNamesAreNotCitiesAndRemoteAllowedMeansRemote() {
		Job job = mapper.map(items.get(1), "FR").orElseThrow();

		assertEquals(List.of(), job.getCities());
		assertEquals(Remote.REMOTE, job.getRemote());
		assertEquals(ContractType.CONTRACT, job.getContractType());
		assertEquals(null, job.getSalaryMin());
	}

	@Test
	void itemWithoutADescriptionIsDropped() {
		assertEquals(Optional.empty(), mapper.map(items.get(2), "FR"));
	}

	private static List<JsonNode> fixture() {
		try (var stream = LinkedInMapperTest.class.getResourceAsStream("/fixtures/linkedin.json")) {
			return new ObjectMapper().readTree(stream).valueStream().toList();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
