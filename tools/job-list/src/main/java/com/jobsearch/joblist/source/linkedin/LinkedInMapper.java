package com.jobsearch.joblist.source.linkedin;

import com.jobsearch.joblist.config.JobListProperties;
import com.jobsearch.joblist.job.ContractType;
import com.jobsearch.joblist.job.Job;
import com.jobsearch.joblist.job.Remote;
import com.jobsearch.joblist.source.HtmlText;
import static com.jobsearch.joblist.source.JsonFields.text;
import com.jobsearch.joblist.source.SourceName;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * One item of the LinkedIn actor to a {@link Job}. LinkedIn gives a free-text location without a country, so the
 * country is the one the search was restricted to.
 */
@Component
class LinkedInMapper {

	private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");

	private final int maxDescriptionChars;

	LinkedInMapper(JobListProperties properties) {
		this.maxDescriptionChars = properties.maxDescriptionChars();
	}

	/** @param country the ISO country the search was restricted to */
	Optional<Job> map(JsonNode item, String country) {
		String id = text(item, "id");
		String url = text(item, "link");
		String title = text(item, "title");
		String company = text(item, "companyName");
		String description = HtmlText.clean(text(item, "descriptionText"), maxDescriptionChars);
		if (id == null || url == null || title == null || company == null || description.isBlank()) {
			return Optional.empty();
		}
		Job job = new Job();
		job.setSource(SourceName.LINKEDIN);
		job.setExternalId(id);
		job.setUrl(url);
		job.setTitle(title);
		job.setCompany(company);
		job.setDescriptionText(description);
		job.setCountries(List.of(country));
		job.setCities(city(text(item, "location")));
		job.setRemote(remote(item));
		job.setContractType(contractType(text(item, "employmentType")));
		applySalary(job, item.path("salaryInfo"));
		job.setPostedAt(postedAt(text(item, "postedAt")));
		return Optional.of(job);
	}

	/** The first part of "Paris, Île-de-France, France", unless the text names an area rather than a city. */
	private static List<String> city(String location) {
		if (location == null) {
			return List.of();
		}
		String first = location.split(",")[0].strip();
		return first.isEmpty() || first.toLowerCase(Locale.ROOT).contains("area") ? List.of() : List.of(first);
	}

	private static Remote remote(JsonNode item) {
		String type = item.path("workplaceTypes").path(0).asString("").toLowerCase(Locale.ROOT);
		if (type.contains("hybrid")) {
			return Remote.HYBRID;
		}
		if (type.contains("remote") || item.path("workRemoteAllowed").asBoolean(false)) {
			return Remote.REMOTE;
		}
		return type.contains("on-site") ? Remote.ON_SITE : Remote.UNKNOWN;
	}

	private static ContractType contractType(String type) {
		if (type == null) {
			return ContractType.UNKNOWN;
		}
		return switch (type.toLowerCase(Locale.ROOT)) {
			case "full-time" -> ContractType.FULL_TIME;
			case "part-time" -> ContractType.PART_TIME;
			case "contract" -> ContractType.CONTRACT;
			case "temporary" -> ContractType.TEMPORARY;
			case "internship" -> ContractType.INTERNSHIP;
			default -> ContractType.OTHER;
		};
	}

	/** {@code salaryInfo} is a list of the lower and upper figure as text ("$17.00"); the period is not given. */
	private static void applySalary(Job job, JsonNode salaryInfo) {
		if (!salaryInfo.isArray() || salaryInfo.isEmpty()) {
			return;
		}
		job.setSalaryMin(number(salaryInfo.path(0).asString("")));
		job.setSalaryMax(number(salaryInfo.path(salaryInfo.size() - 1).asString("")));
		job.setSalaryCurrency(currency(salaryInfo.path(0).asString("")));
	}

	private static BigDecimal number(String text) {
		Matcher matcher = NUMBER.matcher(text);
		return matcher.find() ? new BigDecimal(matcher.group().replace(",", "")) : null;
	}

	private static String currency(String text) {
		if (text.contains("€")) {
			return "EUR";
		}
		if (text.contains("£")) {
			return "GBP";
		}
		return text.contains("$") ? "USD" : null;
	}

	private static Instant postedAt(String date) {
		try {
			return date == null ? null : LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC);
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}

}
