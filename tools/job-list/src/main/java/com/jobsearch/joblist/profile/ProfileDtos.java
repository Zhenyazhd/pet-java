package com.jobsearch.joblist.profile;

import com.jobsearch.joblist.source.Region;
import com.jobsearch.joblist.source.SourceName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ProfileDtos {

	private ProfileDtos() {
	}

	/** Create and replace use the same shape; {@code ownerRef} cannot change once the profile exists. */
	public record ProfileRequest(
			@NotBlank @Size(max = 100) String ownerRef,
			@NotBlank @Size(max = 100) String name,
			@NotBlank @Size(max = 5000) String expectations,
			@NotEmpty @Size(max = 30) List<@NotBlank @Size(max = 100) String> roles,
			@Size(max = 20) List<@Valid Region> regions,
			List<SourceName> sources,
			@Min(0) @Max(100) Integer minScore
	) {
	}

	public record ProfileResponse(
			long id,
			String ownerRef,
			String name,
			String expectations,
			List<String> roles,
			List<Region> regions,
			List<SourceName> sources,
			/** Every source switched on in this deployment; the profile's own {@code sources} are a subset fixed when it was saved. */
			List<SourceName> enabledSources,
			int minScore,
			String createdAt,
			String updatedAt
	) {
	}
}
