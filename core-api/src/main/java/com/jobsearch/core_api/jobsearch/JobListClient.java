package com.jobsearch.core_api.jobsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jobsearch.core_api.common.BadRequestException;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.common.ServiceUnavailableException;
import com.jobsearch.core_api.common.Strings;
import com.jobsearch.core_api.common.TooManyRequestsException;
import com.jobsearch.core_api.jobsearch.JobSearchDtos.MatchPage;
import com.jobsearch.core_api.jobsearch.JobSearchDtos.RegionDto;
import com.jobsearch.core_api.jobsearch.JobSearchDtos.RunResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

/**
 * job-list's client API (bearer token). It does not know our users: it scopes by client only, so the caller
 * ({@link JobSearchService}) must check {@code ownerRef} on every id it passes in.
 * Failures become the matching core-api exceptions: 404, 409 (job-list's message, e.g. "Nothing to rank"), 429 (job-list's message), 400 (its validation message),
 * and 503 for the network, 5xx and a rejected token.
 */
@Component
public class JobListClient {

	private static final Logger log = LoggerFactory.getLogger(JobListClient.class);
	private static final String UNAVAILABLE = "Job search is temporarily unavailable";
	private static final ParameterizedTypeReference<List<JobListProfile>> PROFILES = new ParameterizedTypeReference<>() {
	};
	private static final ParameterizedTypeReference<List<RunResponse>> RUNS = new ParameterizedTypeReference<>() {
	};

	/** A job-list profile as it comes back: the owner is needed for the ownership check. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record JobListProfile(long id, String ownerRef, String name, String expectations, List<String> roles, List<RegionDto> regions, int minScore) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record UnrankedResponse(int count) {
	}

	/** What job-list takes to create or replace a profile; {@code sources} is left out so it uses its defaults. */
	public record ProfileBody(String ownerRef, String name, String expectations, List<String> roles, List<RegionDto> regions, int minScore) {
	}

	/** The optional filters of a matches page; null means "leave to job-list's default". */
	public record MatchQuery(Integer minScore, Boolean delivered, Integer limit, String cursor) {
	}

	private final RestClient restClient;
	private final RestClient countRestClient;
	private final ObjectMapper objectMapper;

	public JobListClient(@Qualifier("jobListRestClient") RestClient restClient,
			@Qualifier("jobListCountRestClient") RestClient countRestClient, ObjectMapper objectMapper) {
		this.restClient = restClient;
		this.countRestClient = countRestClient;
		this.objectMapper = objectMapper;
	}

	public List<JobListProfile> profiles(String ownerRef) {
		return callBody(() -> restClient.get()
				.uri(b -> b.path("/v1/profiles").queryParam("ownerRef", "{o}").build(ownerRef))
				.retrieve().body(PROFILES));
	}

	public JobListProfile createProfile(ProfileBody body) {
		return callBody(() -> restClient.post().uri("/v1/profiles").contentType(MediaType.APPLICATION_JSON)
				.body(body).retrieve().body(JobListProfile.class));
	}

	public JobListProfile updateProfile(long id, ProfileBody body) {
		return callBody(() -> restClient.put().uri("/v1/profiles/{id}", id).contentType(MediaType.APPLICATION_JSON)
				.body(body).retrieve().body(JobListProfile.class));
	}

	public RunResponse startRun(long profileId) {
		return callBody(() -> restClient.post().uri("/v1/profiles/{id}/runs", profileId).retrieve().body(RunResponse.class));
	}

	/** A rank-only run: scores the profile's stored, not yet ranked postings and calls no job board. */
	public RunResponse startRankRun(long profileId) {
		RunResponse run = callBody(() -> restClient.post().uri("/v1/profiles/{id}/runs?mode=rank", profileId).retrieve().body(RunResponse.class));
		if (!"RANK_ONLY".equals(run.mode())) {
			// An old job-list ignores ?mode=rank and has started a paid full run: refuse to present it as a rank run.
			log.error("job-list too old for rank-only runs: answered mode={} to a rank request", run.mode());
			throw new ServiceUnavailableException(UNAVAILABLE, null);
		}
		return run;
	}

	/** How many stored postings of the profile are still unscored (short read timeout). */
	public int unranked(long profileId) {
		return callBody(() -> countRestClient.get().uri("/v1/profiles/{id}/unranked", profileId).retrieve().body(UnrankedResponse.class)).count();
	}

	public List<RunResponse> runs(long profileId) {
		return callBody(() -> restClient.get().uri("/v1/profiles/{id}/runs", profileId).retrieve().body(RUNS));
	}

	public MatchPage matches(long profileId, MatchQuery query) {
		return callBody(() -> restClient.get().uri(b -> {
			b.path("/v1/profiles/{id}/matches");
			if (query.minScore() != null) {
				b.queryParam("minScore", query.minScore());
			}
			if (query.delivered() != null) {
				b.queryParam("delivered", query.delivered());
			}
			if (query.limit() != null) {
				b.queryParam("limit", query.limit());
			}
			if (query.cursor() != null) {
				b.queryParam("cursor", "{cursor}");
				return b.build(profileId, query.cursor());
			}
			return b.build(profileId);
		}).retrieve().body(MatchPage.class));
	}

	public void ack(long matchId) {
		call(() -> {
			restClient.post().uri("/v1/matches/{id}/ack", matchId).retrieve().toBodilessEntity();
			return null;
		});
	}

	/** A call whose reply must have a body: an empty one is job-list misbehaving. */
	private <T> T callBody(Supplier<T> request) {
		T result = call(request);
		if (result == null) {
			log.warn("job-list answered without a body");
			throw new ServiceUnavailableException(UNAVAILABLE, null);
		}
		return result;
	}

	private <T> T call(Supplier<T> request) {
		try {
			return request.get();
		}
		catch (RestClientResponseException ex) {
			throw map(ex);
		}
		catch (RestClientException ex) {
			log.warn("job-list is unreachable: {}", ex.getMessage());
			throw new ServiceUnavailableException(UNAVAILABLE, null);
		}
	}

	private RuntimeException map(RestClientResponseException ex) {
		int status = ex.getStatusCode().value();
		String message = message(ex);
		if (status == 404) {
			return new NotFoundException(message);
		}
		if (status == 429) {
			return new TooManyRequestsException(message, retryAfter(ex));
		}
		if (status == 409) {
			return new ConflictException(message);
		}
		if (status == 401 || status == 403) {
			log.error("job-list rejected the core-api token (HTTP {}); check JOB_LIST_TOKEN", status);
			return new ServiceUnavailableException(UNAVAILABLE, null);
		}
		if (ex.getStatusCode().is4xxClientError()) {
			return new BadRequestException(message);
		}
		log.warn("job-list answered HTTP {}: {}", status, Strings.abbreviate(ex.getResponseBodyAsString(), 300));
		return new ServiceUnavailableException(UNAVAILABLE, retryAfter(ex));
	}

	/** job-list's {@code ApiError.message}, or the status text when the body is not that. */
	private String message(RestClientResponseException ex) {
		try {
			var node = objectMapper.readTree(ex.getResponseBodyAsString()).path("message");
			if (node.isString() && !node.asString().isBlank()) {
				return Strings.abbreviate(node.asString(), 300);
			}
		}
		catch (RuntimeException ignored) {
			// not JSON: fall back below
		}
		return ex.getStatusText().isBlank() ? "Request refused" : ex.getStatusText();
	}

	private static Duration retryAfter(RestClientResponseException ex) {
		HttpHeaders headers = ex.getResponseHeaders();
		String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
		try {
			return value == null ? null : Duration.ofSeconds(Math.max(0, Long.parseLong(value.strip())));
		}
		catch (NumberFormatException e) {
			return null;
		}
	}
}
