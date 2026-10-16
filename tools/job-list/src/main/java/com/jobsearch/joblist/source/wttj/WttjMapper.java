package com.jobsearch.joblist.source.wttj;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * One item of the Welcome to the Jungle actor to a {@link Job}. Contact people are never mapped. The field names
 * are all in the constants below so they are easy to fix after a real run.
 */
@Component
class WttjMapper {

	private static final String ID = "id";
	private static final String URL = "url";
	private static final String TITLE = "name";
	private static final String COMPANY = "organizationName";
	private static final String DESCRIPTION = "description";
	private static final String SUMMARY = "summary";
	private static final String OFFICES = "offices";
	private static final String OFFICE_CITY = "city";
	private static final String OFFICE_COUNTRY = "country_code";
	private static final String REMOTE = "remote";
	private static final String CONTRACT_TYPE = "contractType";
	private static final String SALARY_MIN = "salaryMin";
	private static final String SALARY_MAX = "salaryMax";
	private static final String SALARY_CURRENCY = "salaryCurrency";
	private static final String SALARY_PERIOD = "salaryPeriod";
	private static final String SKILLS = "skills";
	private static final String TOOLS = "tools";
	private static final String PUBLISHED_AT = "publishedAt";

	private final int maxDescriptionChars;

	WttjMapper(JobListProperties properties) {
		this.maxDescriptionChars = properties.maxDescriptionChars();
	}

	Optional<Job> map(JsonNode item) {
		String id = text(item, ID);
		String url = text(item, URL);
		String title = text(item, TITLE);
		String company = text(item, COMPANY);
		String description = HtmlText.clean(description(item), maxDescriptionChars);
		if (id == null || url == null || title == null || company == null || description.isBlank()) {
			return Optional.empty();
		}
		Job job = new Job();
		job.setSource(SourceName.WTTJ);
		job.setExternalId(id);
		job.setUrl(url);
		job.setTitle(title);
		job.setCompany(company);
		job.setDescriptionText(description);
		job.setCountries(officeValues(item, OFFICE_COUNTRY));
		job.setCities(officeValues(item, OFFICE_CITY));
		job.setRemote(remote(text(item, REMOTE)));
		job.setContractType(contractType(text(item, CONTRACT_TYPE)));
		mapSalary(job, item);
		job.setSkills(merged(item.path(SKILLS), item.path(TOOLS)));
		job.setPostedAt(instant(text(item, PUBLISHED_AT)));
		return Optional.of(job);
	}

	private static void mapSalary(Job job, JsonNode item) {
		job.setSalaryMin(decimal(item, SALARY_MIN));
		job.setSalaryMax(decimal(item, SALARY_MAX));
		job.setSalaryCurrency(text(item, SALARY_CURRENCY));
		job.setSalaryPeriod(salaryPeriod(text(item, SALARY_PERIOD)));
	}

	/** The summary stands in when the actor ran without details. */
	private static String description(JsonNode item) {
		String description = text(item, DESCRIPTION);
		return description != null ? description : text(item, SUMMARY);
	}

	private static List<String> officeValues(JsonNode item, String field) {
		Set<String> values = new LinkedHashSet<>();
		item.path(OFFICES).forEach(office -> {
			String value = text(office, field);
			if (value != null) {
				values.add(value);
			}
		});
		return List.copyOf(values);
	}

	// "punctual" means occasional remote days, which is closer to an office job than to hybrid.
	private static Remote remote(String value) {
		if (value == null) {
			return Remote.UNKNOWN;
		}
		return switch (value.toLowerCase(Locale.ROOT)) {
			case "no", "punctual" -> Remote.ON_SITE;
			case "partial" -> Remote.HYBRID;
			case "fulltime" -> Remote.REMOTE;
			default -> Remote.UNKNOWN;
		};
	}

	private static ContractType contractType(String value) {
		if (value == null) {
			return ContractType.UNKNOWN;
		}
		return switch (value.toLowerCase(Locale.ROOT)) {
			case "full_time" -> ContractType.FULL_TIME;
			case "part_time" -> ContractType.PART_TIME;
			case "freelance" -> ContractType.CONTRACT;
			case "temporary", "fixed_term" -> ContractType.TEMPORARY;
			case "internship", "apprenticeship", "graduate_program" -> ContractType.INTERNSHIP;
			default -> ContractType.OTHER;
		};
	}

	private static String salaryPeriod(String value) {
		if (value == null) {
			return null;
		}
		return switch (value.toLowerCase(Locale.ROOT)) {
			case "yearly" -> "YEAR";
			case "monthly" -> "MONTH";
			case "daily" -> "DAY";
			case "hourly" -> "HOUR";
			default -> null;
		};
	}

	private static List<String> merged(JsonNode first, JsonNode second) {
		Set<String> values = new LinkedHashSet<>();
		first.forEach(node -> values.add(node.asString()));
		second.forEach(node -> values.add(node.asString()));
		return List.copyOf(values);
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
