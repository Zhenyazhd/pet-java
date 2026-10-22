package com.jobsearch.joblist.source;

import com.jobsearch.joblist.config.JobListProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/** The only class that knows the Apify HTTP API: start an actor run, wait for it, read its dataset. */
@Component
public class ApifyClient {

	private static final Set<String> FINISHED = Set.of("SUCCEEDED", "FAILED", "ABORTED", "TIMED-OUT");
	private static final int PAGE_SIZE = 100;
	private static final int READ_ATTEMPTS = 3;

	private final RestClient restClient;
	private final JobListProperties.Apify settings;

	public ApifyClient(@Qualifier("apifyRestClient") RestClient restClient, JobListProperties properties) {
		this.restClient = restClient;
		this.settings = properties.apify();
	}

	/** An actor run as Apify reports it; {@code costUsd} is what the run was charged, null while unknown. */
	public record Run(String id, String status, String datasetId, BigDecimal costUsd) {

		public boolean succeeded() {
			return "SUCCEEDED".equals(status);
		}

		/** False for a run that was stopped from outside: its reported cost is a snapshot taken while it still billed. */
		public boolean endedOnItsOwn() {
			return Set.of("SUCCEEDED", "FAILED", "TIMED-OUT").contains(status);
		}
	}

	/**
	 * Starts the actor with a hard cap on what it may charge and a time limit (the run timeout), so a run nobody is
	 * waiting for any more (the service died) ends by itself when the reservation for it does; actor ids look like "owner/name".
	 */
	public Run start(String actorId, JsonNode input) {
		JsonNode response = restClient.post()
				.uri("/v2/acts/{actor}/runs?maxTotalChargeUsd={cap}&timeout={seconds}", actorId.replace('/', '~'),
						settings.maxCostPerSourceUsd(), settings.runTimeout().toSeconds())
				.contentType(MediaType.APPLICATION_JSON)
				.body(input)
				.retrieve()
				.body(JsonNode.class);
		return toRun(response.path("data"));
	}

	/** Polls until the run has finished or {@code run-timeout} has passed; a run still going then is aborted. */
	public Run awaitFinished(Run started) {
		Instant deadline = Instant.now().plus(settings.runTimeout());
		Run run = started;
		while (!FINISHED.contains(run.status())) {
			if (Instant.now().isAfter(deadline)) {
				return abort(run.id());
			}
			sleep(settings.pollInterval());
			String id = run.id();
			run = toRun(retrying(() -> restClient.get().uri("/v2/actor-runs/{id}", id).retrieve().body(JsonNode.class)).path("data"));
		}
		return run;
	}

	/** Reads up to {@code limit} items of a dataset, page by page. */
	public List<JsonNode> items(String datasetId, int limit) {
		List<JsonNode> items = new ArrayList<>();
		while (items.size() < limit) {
			int pageSize = Math.min(PAGE_SIZE, limit - items.size());
			int offset = items.size();
			JsonNode page = retrying(() -> restClient.get()
					.uri("/v2/datasets/{id}/items?clean=true&format=json&offset={offset}&limit={limit}", datasetId, offset, pageSize)
					.retrieve()
					.body(JsonNode.class));
			page.forEach(items::add);
			if (page.size() < pageSize) {
				break;
			}
		}
		return items;
	}

	/**
	 * A read that failed for a reason that may pass (the network, a 5xx, 429) is tried again a few times: a poll that
	 * fails once must not throw away a run that has already been paid for. Other errors (4xx) are not retried.
	 */
	private <T> T retrying(Supplier<T> read) {
		for (int attempt = 1; ; attempt++) {
			try {
				return read.get();
			}
			catch (RestClientResponseException ex) {
				if (attempt == READ_ATTEMPTS || !(ex.getStatusCode().is5xxServerError() || ex.getStatusCode().value() == 429)) {
					throw ex;
				}
			}
			catch (ResourceAccessException ex) {
				if (attempt == READ_ATTEMPTS) {
					throw ex;
				}
			}
			sleep(settings.pollInterval());
		}
	}

	/** Stops a run that is no longer wanted; the answer does not matter, so a failure here is ignored. */
	public void abortQuietly(String runId) {
		try {
			abort(runId);
		}
		catch (RuntimeException ex) {
			// Nothing more can be done; the run is capped by maxTotalChargeUsd anyway.
		}
	}

	private Run abort(String runId) {
		JsonNode response = restClient.post().uri("/v2/actor-runs/{id}/abort", runId).retrieve().body(JsonNode.class);
		return toRun(response.path("data"));
	}

	private static Run toRun(JsonNode data) {
		JsonNode usage = data.path("usageTotalUsd");
		return new Run(
				data.path("id").asString(),
				data.path("status").asString(),
				data.path("defaultDatasetId").asString(null),
				usage.isNumber() ? usage.decimalValue() : null
		);
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for Apify", ex);
		}
	}
}
