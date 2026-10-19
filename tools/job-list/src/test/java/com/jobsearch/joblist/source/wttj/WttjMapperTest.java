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

	/** Items as the real actor returned them (run of 2026-10-19): skills are objects named in several languages. */
	@Test
	void mapsTheItemsTheRealActorReturned() {
		JsonNode real = fixture("/fixtures/wttj-real.json");

		Job first = mapper.map(real.get(0)).orElseThrow();
		assertThat(first.getSkills()).containsExactly("Goal setting", "React", "GitHub");
		assertThat(first.getContractType()).isEqualTo(ContractType.INTERNSHIP);
		assertThat(first.getRemote()).isEqualTo(Remote.HYBRID);
		assertThat(first.getSalaryMin()).isNull();
		assertThat(first.getPostedAt()).isEqualTo(Instant.parse("2026-10-19T12:05:35Z"));

		Job second = mapper.map(real.get(1)).orElseThrow();
		assertThat(second.getSkills()).containsExactly("Teamwork", "Méthode Agile", "Hibernate");

		assertThat(mapper.map(real.get(2)).orElseThrow().getSkills()).isEmpty();
	}

	@Test
	void anOddSkillShapeIsSkippedNotAnError() {
		JsonNode item = JsonMapper.builder().build().readTree("""
				{"id":"1","url":"https://x.test/1","name":"Dev","organizationName":"Acme","description":"Text",
				 "skills":[{"name":{}}, 42, null, {"reference":"r"}, "Rust", {"name":["a"]}], "tools":[{"name":""}]}
				""");

		assertThat(mapper.map(item).orElseThrow().getSkills()).containsExactly("Rust");
	}

	@Test
	void neverThrowsOnAnEmptyItem() {
		assertThat(mapper.map(JsonMapper.builder().build().createObjectNode())).isEmpty();
	}

	private static JsonNode fixture() {
		return fixture("/fixtures/wttj.json");
	}

	private static JsonNode fixture(String path) {
		try (InputStream stream = WttjMapperTest.class.getResourceAsStream(path)) {
			return JsonMapper.builder().build().readTree(stream);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
