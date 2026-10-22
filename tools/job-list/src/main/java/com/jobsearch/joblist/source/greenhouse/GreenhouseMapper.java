package com.jobsearch.joblist.source.greenhouse;

import com.jobsearch.joblist.config.JobListProperties;
import com.jobsearch.joblist.job.ContractType;
import com.jobsearch.joblist.job.Job;
import com.jobsearch.joblist.job.Remote;
import com.jobsearch.joblist.source.HtmlText;
import static com.jobsearch.joblist.source.JsonFields.decimal;
import static com.jobsearch.joblist.source.JsonFields.instant;
import static com.jobsearch.joblist.source.JsonFields.text;
import com.jobsearch.joblist.source.SourceName;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * One item of the Greenhouse actor to a {@link Job}. The field names are all in the constants below so they are
 * easy to fix after a real run.
 */
@Component
class GreenhouseMapper {

	private static final String ID = "id";
	private static final String URL = "url";
	private static final String TITLE = "title";
	private static final String COMPANY = "organization";
	private static final String DESCRIPTION = "description_text";
	private static final String CITIES = "cities_derived";
	private static final String COUNTRIES = "countries_derived";
	private static final String WORK_ARRANGEMENT = "ai_work_arrangement";
	private static final String EMPLOYMENT_TYPE = "ai_employment_type";
	private static final String SALARY_MIN = "ai_salary_min_value";
	private static final String SALARY_MAX = "ai_salary_max_value";
	private static final String SALARY_CURRENCY = "ai_salary_currency";
	private static final String SALARY_UNIT = "ai_salary_unit_text";
	private static final String SKILLS = "ai_key_skills";
	private static final String POSTED_AT = "date_posted";

	private static final Map<String, String> COUNTRY_CODES = countryCodesByEnglishName();

	private final int maxDescriptionChars;

	GreenhouseMapper(JobListProperties properties) {
		this.maxDescriptionChars = properties.maxDescriptionChars();
	}

	Optional<Job> map(JsonNode item) {
		String id = text(item, ID);
		String url = text(item, URL);
		String title = text(item, TITLE);
		String company = text(item, COMPANY);
		String description = HtmlText.clean(text(item, DESCRIPTION), maxDescriptionChars);
		if (id == null || url == null || title == null || company == null || description.isBlank()) {
			return Optional.empty();
		}
		Job job = new Job();
		job.setSource(SourceName.GREENHOUSE);
		job.setExternalId(id);
		job.setUrl(url);
		job.setTitle(title);
		job.setCompany(company);
		job.setDescriptionText(description);
		job.setCountries(countryCodes(item.path(COUNTRIES)));
		job.setCities(strings(item.path(CITIES)));
		job.setRemote(remote(text(item, WORK_ARRANGEMENT)));
		job.setContractType(contractType(item.path(EMPLOYMENT_TYPE)));
		mapSalary(job, item);
		job.setSkills(strings(item.path(SKILLS)));
		job.setPostedAt(instant(text(item, POSTED_AT)));
		return Optional.of(job);
	}

	/** The salary is left out when its unit is not one we store (for example per week), as the figures would be misread. */
	private static void mapSalary(Job job, JsonNode item) {
		String period = salaryPeriod(text(item, SALARY_UNIT));
		if (period == null) {
			return;
		}
		job.setSalaryMin(decimal(item, SALARY_MIN));
		job.setSalaryMax(decimal(item, SALARY_MAX));
		job.setSalaryCurrency(text(item, SALARY_CURRENCY));
		job.setSalaryPeriod(period);
	}

	private static String salaryPeriod(String unit) {
		if (unit == null) {
			return null;
		}
		String period = unit.toUpperCase(Locale.ROOT);
		return List.of("HOUR", "DAY", "MONTH", "YEAR").contains(period) ? period : null;
	}

	// "Remote OK" is remote with an optional office, so it counts as remote.
	private static Remote remote(String value) {
		if (value == null) {
			return Remote.UNKNOWN;
		}
		return switch (value.toLowerCase(Locale.ROOT)) {
			case "on-site" -> Remote.ON_SITE;
			case "hybrid" -> Remote.HYBRID;
			case "remote ok", "remote solely" -> Remote.REMOTE;
			default -> Remote.UNKNOWN;
		};
	}

	/** The first listed type wins. */
	private static ContractType contractType(JsonNode types) {
		if (!types.isArray() || types.isEmpty()) {
			return ContractType.UNKNOWN;
		}
		return switch (types.get(0).asString().toUpperCase(Locale.ROOT)) {
			case "FULL_TIME" -> ContractType.FULL_TIME;
			case "PART_TIME" -> ContractType.PART_TIME;
			case "CONTRACTOR" -> ContractType.CONTRACT;
			case "TEMPORARY" -> ContractType.TEMPORARY;
			case "INTERN" -> ContractType.INTERNSHIP;
			default -> ContractType.OTHER;
		};
	}

	private static List<String> countryCodes(JsonNode names) {
		Set<String> codes = new LinkedHashSet<>();
		names.forEach(name -> {
			String code = COUNTRY_CODES.get(name.asString().toLowerCase(Locale.ROOT));
			if (code != null) {
				codes.add(code);
			}
		});
		return List.copyOf(codes);
	}

	private static Map<String, String> countryCodesByEnglishName() {
		Map<String, String> codes = new HashMap<>();
		for (String code : Locale.getISOCountries()) {
			String name = new Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH);
			codes.put(name.toLowerCase(Locale.ROOT), code);
		}
		return codes;
	}

	private static List<String> strings(JsonNode node) {
		Set<String> values = new LinkedHashSet<>();
		node.forEach(value -> values.add(value.asString()));
		return List.copyOf(values);
	}
}
