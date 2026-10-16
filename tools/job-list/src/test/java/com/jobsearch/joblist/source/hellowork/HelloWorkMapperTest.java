package com.jobsearch.joblist.source.hellowork;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class HelloWorkMapperTest {

	private final HelloWorkMapper mapper = new HelloWorkMapper(TestProperties.defaults());
	private final List<JsonNode> items = fixture();

	@Test
	void completeItemBecomesAJob() {
		Job job = mapper.map(items.get(0)).orElseThrow();

		assertEquals(SourceName.HELLOWORK, job.getSource());
		assertEquals("77200967", job.getExternalId());
		assertEquals("Développeur Python", job.getTitle());
		assertEquals("XEFI", job.getCompany());
		assertEquals(List.of("FR"), job.getCountries());
		assertEquals(List.of("Rillieux-la-Pape"), job.getCities());
		assertEquals(Remote.HYBRID, job.getRemote());
		assertEquals(ContractType.FULL_TIME, job.getContractType());
		assertEquals(0, new BigDecimal("26400").compareTo(job.getSalaryMin()));
		assertEquals("YEAR", job.getSalaryPeriod());
		assertEquals(List.of("Python", "Django"), job.getSkills());
		assertEquals(Instant.parse("2026-06-25T00:13:43Z"), job.getPostedAt());
		assertTrue(job.getDescriptionText().contains("Développer en Python"), job.getDescriptionText());
		assertTrue(!job.getDescriptionText().contains("<li>"));
	}

	@Test
	void frenchContractNameIsTheFallbackAndMissingOptionalFieldsAreFine() {
		Job job = mapper.map(items.get(1)).orElseThrow();

		assertEquals(ContractType.INTERNSHIP, job.getContractType());
		assertEquals(Remote.ON_SITE, job.getRemote());
		assertEquals(null, job.getSalaryMin());
		assertEquals(List.of(), job.getSkills());
	}

	@Test
	void itemWithoutADescriptionIsDropped() {
		assertEquals(Optional.empty(), mapper.map(items.get(2)));
	}

	private static List<JsonNode> fixture() {
		try (var stream = HelloWorkMapperTest.class.getResourceAsStream("/fixtures/hellowork.json")) {
			return new ObjectMapper().readTree(stream).valueStream().toList();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
