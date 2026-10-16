package com.jobsearch.joblist.profile;

import com.jobsearch.joblist.common.BadRequestException;
import com.jobsearch.joblist.common.NotFoundException;
import com.jobsearch.joblist.config.JobListProperties;
import com.jobsearch.joblist.profile.ProfileDtos.ProfileRequest;
import com.jobsearch.joblist.profile.ProfileDtos.ProfileResponse;
import com.jobsearch.joblist.source.Region;
import com.jobsearch.joblist.source.SourceName;
import com.jobsearch.joblist.source.SourceQuery;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Search profiles of one client; every method is scoped by the calling client's name. */
@Service
@Transactional
public class SearchProfileService {

	private static final int DEFAULT_MIN_SCORE = 70;
	private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());
	private static final TypeReference<List<Region>> REGIONS = new TypeReference<>() {
	};

	private final SearchProfileRepository repository;
	private final JobListProperties properties;
	private final ObjectMapper objectMapper;

	public SearchProfileService(
			SearchProfileRepository repository,
			JobListProperties properties,
			ObjectMapper objectMapper
	) {
		this.repository = repository;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	public ProfileResponse create(String client, ProfileRequest request) {
		String ownerRef = request.ownerRef().strip();
		if (repository.countByClient(client) >= properties.maxProfilesPerClient()) {
			throw new BadRequestException("This client already has " + properties.maxProfilesPerClient() + " profiles");
		}
		if (repository.countByClientAndOwnerRef(client, ownerRef) >= properties.maxProfilesPerOwner()) {
			throw new BadRequestException("At most " + properties.maxProfilesPerOwner() + " profiles per owner");
		}
		SearchProfile profile = new SearchProfile();
		profile.setClient(client);
		profile.setOwnerRef(ownerRef);
		profile.setCreatedAt(Instant.now());
		apply(profile, request);
		return toResponse(persist(profile));
	}

	/** Updates the owner's profile of that name, or creates it. */
	public ProfileResponse save(String client, ProfileRequest request) {
		return repository.findByClientAndOwnerRefAndName(client, request.ownerRef().strip(), request.name().strip())
				.map(existing -> update(client, existing.getId(), request))
				.orElseGet(() -> create(client, request));
	}

	public ProfileResponse update(String client, long id, ProfileRequest request) {
		SearchProfile profile = owned(client, id);
		if (!profile.getOwnerRef().equals(request.ownerRef().strip())) {
			throw new BadRequestException("ownerRef cannot be changed");
		}
		apply(profile, request);
		return toResponse(persist(profile));
	}

	@Transactional(readOnly = true)
	public ProfileResponse get(String client, long id) {
		return toResponse(owned(client, id));
	}

	@Transactional(readOnly = true)
	public List<ProfileResponse> list(String client, String ownerRef) {
		List<SearchProfile> profiles = ownerRef == null
				? repository.findByClientOrderById(client)
				: repository.findByClientAndOwnerRefOrderById(client, ownerRef.strip());
		return profiles.stream().map(this::toResponse).toList();
	}

	public void delete(String client, long id) {
		repository.delete(owned(client, id));
	}

	/** The profile if it belongs to the client; another client's profile looks like a missing one. */
	public SearchProfile owned(String client, long id) {
		return repository.findByIdAndClient(id, client)
				.orElseThrow(() -> new NotFoundException("Profile not found: " + id));
	}

	private void apply(SearchProfile profile, ProfileRequest request) {
		profile.setName(request.name().strip());
		profile.setExpectations(request.expectations().strip());
		profile.setRoles(request.roles().stream().map(String::strip).distinct().toList());
		profile.setRegions(objectMapper.writeValueAsString(regions(request.regions())));
		profile.setSources(sourceNames(request.sources()));
		profile.setMinScore(request.minScore() == null ? DEFAULT_MIN_SCORE : request.minScore());
		profile.setUpdatedAt(Instant.now());
	}

	/** The regions without blank cities, each with a known country. */
	private static List<Region> regions(List<Region> requested) {
		if (requested == null) {
			return List.of();
		}
		return requested.stream().map(region -> {
			if (!ISO_COUNTRIES.contains(region.country())) {
				throw new BadRequestException("regions: '" + region.country() + "' is not an ISO 3166-1 alpha-2 code in upper case, e.g. FR (Britain is GB)");
			}
			boolean hasCity = region.city() != null && !region.city().isBlank();
			return new Region(region.country(), hasCity ? region.city().strip() : null);
		}).distinct().toList();
	}

	/** What to ask the job boards for on behalf of the profile. */
	public SourceQuery query(SearchProfile profile) {
		SourceQuery query = new SourceQuery();
		query.setRoles(profile.getRoles());
		query.setRegions(objectMapper.readValue(profile.getRegions(), REGIONS));
		return query;
	}

	private List<String> sourceNames(List<SourceName> requested) {
		List<SourceName> sources = requested == null || requested.isEmpty() ? enabledSources() : requested;
		for (SourceName source : sources) {
			if (!properties.source(source).enabled()) {
				throw new BadRequestException("Source " + source + " is not enabled");
			}
		}
		return sources.stream().distinct().map(Enum::name).toList();
	}

	private List<SourceName> enabledSources() {
		return Arrays.stream(SourceName.values()).filter(source -> properties.source(source).enabled()).toList();
	}

	private SearchProfile persist(SearchProfile profile) {
		try {
			return repository.saveAndFlush(profile);
		}
		catch (DataIntegrityViolationException ex) {
			if (!String.valueOf(ex.getMostSpecificCause().getMessage()).contains("uq_search_profile_owner_name")) {
				throw ex;
			}
			throw new BadRequestException("This owner already has a profile named '" + profile.getName() + "'");
		}
	}

	private ProfileResponse toResponse(SearchProfile profile) {
		return new ProfileResponse(
				profile.getId(),
				profile.getOwnerRef(),
				profile.getName(),
				profile.getExpectations(),
				profile.getRoles(),
				objectMapper.readValue(profile.getRegions(), REGIONS),
				profile.getSources().stream().map(SourceName::valueOf).toList(),
				profile.getMinScore(),
				profile.getCreatedAt().toString(),
				profile.getUpdatedAt().toString()
		);
	}
}
