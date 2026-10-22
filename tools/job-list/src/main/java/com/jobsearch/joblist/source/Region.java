package com.jobsearch.joblist.source;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/** A place to search: an ISO 3166-1 alpha-2 country code in upper case and, optionally, a city. */
public record Region(@NotBlank @Size(max = 2) String country, @Size(max = 100) String city) {

	public String place() {
		return city != null ? city : new Locale.Builder().setRegion(country).build().getDisplayCountry(Locale.ENGLISH);
	}
}
