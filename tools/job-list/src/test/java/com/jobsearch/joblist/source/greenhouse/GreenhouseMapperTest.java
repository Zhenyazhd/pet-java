package com.jobsearch.joblist.source.greenhouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsearch.joblist.TestProperties;
import com.jobsearch.joblist.job.ContractType;
import com.jobsearch.joblist.job.Job;
import com.jobsearch.joblist.job.Remote;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GreenhouseMapperTest {

	private final GreenhouseMapper mapper = new GreenhouseMapper(TestProperties.defaults());
	private final JsonNode items = fixture();

	@Test
	void mapsACompleteItem() {
		Job job = mapper.map(items.get(0)).orElseThrow();

		assertThat(job.getExternalId()).isEqualTo("123456789");
		assertThat(job.getTitle()).isEqualTo("Senior Backend Engineer");
		assertThat(job.getCompany()).isEqualTo("Acme");
		assertThat(job.getDescriptionText()).contains("senior backend engineer.\n\nYou will own");
		assertThat(job.getCountries()).containsExactly("FR");
		assertThat(job.getCities()).containsExactly("Paris");
		assertThat(job.getRemote()).isEqualTo(Remote.HYBRID);
		assertThat(job.getContractType()).isEqualTo(ContractType.FULL_TIME);
		assertThat(job.getSalaryMin()).isEqualByComparingTo(new BigDecimal("60000"));
		assertThat(job.getSalaryMax()).isEqualByComparingTo(new BigDecimal("80000"));
		assertThat(job.getSalaryCurrency()).isEqualTo("EUR");
		assertThat(job.getSalaryPeriod()).isEqualTo("YEAR");
		assertThat(job.getSkills()).containsExactly("Java", "Kafka");
		assertThat(job.getPostedAt()).isEqualTo(Instant.parse("2026-10-01T08:00:00Z"));
	}

	@Test
	void dropsAnItemWithoutCompany() {
		assertThat(mapper.map(items.get(1))).isEmpty();
	}

	@Test
	void mapsARemoteInternItemAndSkipsWhatItCannotStore() {
		Job job = mapper.map(items.get(2)).orElseThrow();

		assertThat(job.getRemote()).isEqualTo(Remote.REMOTE);
		assertThat(job.getContractType()).isEqualTo(ContractType.INTERNSHIP);
		assertThat(job.getCountries()).isEmpty();
		assertThat(job.getSalaryMin()).isNull();
		assertThat(job.getSalaryPeriod()).isNull();
	}

	@Test
	void neverThrowsOnAnEmptyItem() {
		assertThat(mapper.map(JsonMapper.builder().build().createObjectNode())).isEmpty();
	}

	private static JsonNode fixture() {
		try (InputStream stream = GreenhouseMapperTest.class.getResourceAsStream("/fixtures/greenhouse.json")) {
			return JsonMapper.builder().build().readTree(stream);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
