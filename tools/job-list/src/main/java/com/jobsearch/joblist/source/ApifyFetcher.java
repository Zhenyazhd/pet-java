package com.jobsearch.joblist.source;

import com.jobsearch.joblist.common.HttpErrors;
import com.jobsearch.joblist.config.JobListProperties;
import com.jobsearch.joblist.job.Job;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * Runs one actor and maps its items, the same way for every source: start, wait, read the dataset, map each item,
 * and fail the source when too many items could not be mapped (the sign that the actor's output format changed).
 */
@Component
public class ApifyFetcher {

	private static final Logger log = LoggerFactory.getLogger(ApifyFetcher.class);

	private final ApifyClient apify;
	private final JobListProperties properties;

	public ApifyFetcher(ApifyClient apify, JobListProperties properties) {
		this.apify = apify;
		this.properties = properties;
	}

	/** @param mapper turns one item into a job, or empty when the item is not usable */
	public SourceResult fetch(
			SourceName source,
			String actorId,
			JsonNode input,
			int maxItems,
			Function<JsonNode, Optional<Job>> mapper
	) {
		ApifyClient.Run started;
		try {
			started = apify.start(actorId, input);
		}
		catch (RestClientResponseException ex) {
			log.warn("Apify refused to start the actor source={} error={}", source, HttpErrors.describe(ex));
			// A 4xx is a refusal: nothing ran. A 5xx (or 408/429 from a gateway) may have come after the run was created.
			BigDecimal cost = refusedForSure(ex) ? BigDecimal.ZERO : properties.apify().maxCostPerSourceUsd();
			return SourceResult.failed(null, cost, "Apify did not accept the request");
		}
		catch (RestClientException ex) {
			log.warn("Apify did not answer the start request source={} error={}", source, HttpErrors.describe(ex));
			return SourceResult.failed(null, properties.apify().maxCostPerSourceUsd(), "Apify did not answer the request");
		}
		// From here a run exists and bills, so whatever goes wrong its cost is counted.
		ApifyClient.Run run;
		try {
			run = apify.awaitFinished(started);
		}
		catch (RuntimeException ex) {
			log.warn("Waiting for the actor failed source={} error={}", source, HttpErrors.describe(ex));
			apify.abortQuietly(started.id());
			return SourceResult.failed(started.id(), properties.apify().maxCostPerSourceUsd(), "Reading the actor's result failed");
		}
		BigDecimal cost = costOf(run);
		if (!run.succeeded()) {
			return SourceResult.failed(run.id(), cost, "Actor run ended as " + run.status());
		}
		List<JsonNode> items;
		try {
			items = apify.items(run.datasetId(), maxItems);
		}
		catch (RuntimeException ex) {
			// The run is over and its cost is known, so that is what is counted, not the worst case.
			log.warn("Reading the actor's dataset failed source={} error={}", source, HttpErrors.describe(ex));
			return SourceResult.failed(run.id(), cost, "Reading the actor's result failed");
		}
		return mapItems(source, run, cost, items, mapper);
	}

	private SourceResult mapItems(
			SourceName source,
			ApifyClient.Run run,
			BigDecimal cost,
			List<JsonNode> items,
			Function<JsonNode, Optional<Job>> mapper
	) {
		List<Job> jobs = new ArrayList<>();
		for (JsonNode item : items) {
			mapSafely(item, mapper).ifPresent(jobs::add);
		}
		int dropped = items.size() - jobs.size();
		log.info("Source mapped source={} items={} dropped={} costUsd={}", source, items.size(), dropped, cost);
		if (!items.isEmpty() && (double) dropped / items.size() > properties.mapperDropThreshold()) {
			return SourceResult.failed(run.id(), cost, "Too many items could not be read (" + dropped + " of "
					+ items.size() + "): output format changed?");
		}
		return new SourceResult(jobs, items.size(), dropped, cost, run.id(), null);
	}

	private static Optional<Job> mapSafely(JsonNode item, Function<JsonNode, Optional<Job>> mapper) {
		try {
			return mapper.apply(item);
		}
		catch (RuntimeException ex) {
			log.warn("An item could not be mapped error={}", ex.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	private static boolean refusedForSure(RestClientResponseException ex) {
		int status = ex.getStatusCode().value();
		return ex.getStatusCode().is4xxClientError() && status != 408 && status != 429;
	}

	/** What the run cost: its reported cost, or the worst case when none was reported or the run was cut short. */
	private BigDecimal costOf(ApifyClient.Run run) {
		BigDecimal reported = run.costUsd();
		boolean known = run.endedOnItsOwn() && reported != null && reported.signum() > 0;
		return known ? reported : properties.apify().maxCostPerSourceUsd();
	}
}
