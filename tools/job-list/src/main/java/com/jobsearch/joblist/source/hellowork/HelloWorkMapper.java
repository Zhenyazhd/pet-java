package com.jobsearch.joblist.source.hellowork;

import com.jobsearch.joblist.config.JobListProperties;
import com.jobsearch.joblist.job.ContractType;
import com.jobsearch.joblist.job.Job;
import com.jobsearch.joblist.job.Remote;
import com.jobsearch.joblist.source.HtmlText;
import static com.jobsearch.joblist.source.JsonFields.decimal;
import static com.jobsearch.joblist.source.JsonFields.text;
import com.jobsearch.joblist.source.SourceName;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** One item of the HelloWork actor (snake_case fields) to a {@link Job}. */
@Component
class HelloWorkMapper {

	private final int maxDescriptionChars;

	HelloWorkMapper(JobListProperties properties) {
		this.maxDescriptionChars = properties.maxDescriptionChars();
	}

	Optional<Job> map(JsonNode item) {
		String id = text(item, "id");
		String url = text(item, "url");
		String title = text(item, "title");
		String company = text(item, "company");
		String description = HtmlText.clean(text(item, "description"), maxDescriptionChars);
		if (id == null || url == null || title == null || company == null || description.isBlank()) {
			return Optional.empty();
		}
		Job job = new Job();
		job.setSource(SourceName.HELLOWORK);
		job.setExternalId(id);
		job.setUrl(url);
		job.setTitle(title);
		job.setCompany(company);
		job.setDescriptionText(description);
		job.setCountries(List.of("FR"));
		job.setCities(text(item, "city") == null ? List.of() : List.of(text(item, "city")));
		job.setRemote(remote(text(item, "remote")));
		job.setContractType(contractType(text(item, "employment_type"), text(item, "contract_type")));
		job.setSalaryMin(decimal(item, "salary_min"));
		job.setSalaryMax(decimal(item, "salary_max"));
		job.setSalaryCurrency(text(item, "salary_currency"));
		job.setSalaryPeriod(text(item, "salary_period") == null ? null : text(item, "salary_period").toUpperCase(Locale.ROOT));
		job.setSkills(skills(item.path("skills")));
		job.setPostedAt(instant(text(item, "published_at")));
		return Optional.of(job);
	}

	private static Remote remote(String value) {
		if (value == null) {
			return Remote.UNKNOWN;
		}
		return switch (value.toLowerCase(Locale.ROOT)) {
			case "no" -> Remote.ON_SITE;
			case "partial" -> Remote.HYBRID;
			case "full", "fulltime", "total" -> Remote.REMOTE;
			default -> Remote.UNKNOWN;
		};
	}

	/** The structured employment type wins; the French contract name is the fallback. */
	private static ContractType contractType(String employmentType, String contractType) {
		if (employmentType != null) {
			return switch (employmentType.toUpperCase(Locale.ROOT)) {
				case "FULL_TIME" -> ContractType.FULL_TIME;
				case "PART_TIME" -> ContractType.PART_TIME;
				case "CONTRACTOR" -> ContractType.CONTRACT;
				case "TEMPORARY" -> ContractType.TEMPORARY;
				case "INTERN" -> ContractType.INTERNSHIP;
				default -> ContractType.OTHER;
			};
		}
		if (contractType == null) {
			return ContractType.UNKNOWN;
		}
		return switch (contractType.toUpperCase(Locale.ROOT)) {
			case "CDI" -> ContractType.FULL_TIME;
			case "CDD" -> ContractType.TEMPORARY;
			case "FREELANCE" -> ContractType.CONTRACT;
			case "STAGE", "ALTERNANCE" -> ContractType.INTERNSHIP;
			default -> ContractType.OTHER;
		};
	}

	private static List<String> skills(JsonNode node) {
		List<String> skills = new ArrayList<>();
		node.forEach(skill -> skills.add(skill.asString()));
		return skills;
	}

	private static Instant instant(String value) {
		try {
			return value == null ? null : Instant.parse(value);
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}
}
