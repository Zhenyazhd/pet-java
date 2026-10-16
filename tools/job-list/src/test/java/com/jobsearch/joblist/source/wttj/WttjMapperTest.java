package com.jobsearch.joblist.source.wttj;

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

class WttjMapperTest {

	private final WttjMapper mapper = new WttjMapper(TestProperties.defaults());
	private final JsonNode items = fixture();

	@Test
	void mapsACompleteItem() {
		Job job = mapper.map(items.get(0)).orElseThrow();

		assertThat(job.getExternalId()).isEqualTo("ed60e86c-707f-434e-bb10-17eeba802759");
		assertThat(job.getTitle()).isEqualTo("Responsable du service logistique H/F");
		assertThat(job.getCompany()).isEqualTo("Elis");
		assertThat(job.getDescriptionText()).startsWith("Ne venez pas chez Elis...").contains("- Vous construisez les plannings");
		assertThat(job.getCountries()).containsExactly("FR");
		assertThat(job.getCities()).containsExactly("Saint-Louis", "Mulhouse");
		assertThat(job.getRemote()).isEqualTo(Remote.HYBRID);
		assertThat(job.getContractType()).isEqualTo(ContractType.FULL_TIME);
		assertThat(job.getSalaryMin()).isEqualByComparingTo(new BigDecimal("45000"));
		assertThat(job.getSalaryMax()).isEqualByComparingTo(new BigDecimal("50000"));
		assertThat(job.getSalaryCurrency()).isEqualTo("EUR");
		assertThat(job.getSalaryPeriod()).isEqualTo("YEAR");
		assertThat(job.getSkills()).containsExactly("Logistique", "SAP");
		assertThat(job.getPostedAt()).isEqualTo(Instant.parse("2026-02-27T22:39:00Z"));
	}

	@Test
	void dropsAnItemWithoutCompany() {
		assertThat(mapper.map(items.get(1))).isEmpty();
	}

	@Test
	void mapsARemoteItemWithoutSalaryAndFallsBackToTheSummary() {
		Job job = mapper.map(items.get(2)).orElseThrow();

		assertThat(job.getDescriptionText()).isEqualTo("Build APIs at Acme.");
		assertThat(job.getRemote()).isEqualTo(Remote.REMOTE);
		assertThat(job.getContractType()).isEqualTo(ContractType.CONTRACT);
		assertThat(job.getSalaryMin()).isNull();
		assertThat(job.getSalaryPeriod()).isNull();
		assertThat(job.getCountries()).isEmpty();
		assertThat(job.getSkills()).isEmpty();
	}

	@Test
	void neverThrowsOnAnEmptyItem() {
		assertThat(mapper.map(JsonMapper.builder().build().createObjectNode())).isEmpty();
	}

	private static JsonNode fixture() {
		try (InputStream stream = WttjMapperTest.class.getResourceAsStream("/fixtures/wttj.json")) {
			return JsonMapper.builder().build().readTree(stream);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
