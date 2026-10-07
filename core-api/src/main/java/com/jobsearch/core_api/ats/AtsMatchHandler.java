package com.jobsearch.core_api.ats;

import com.jobsearch.core_api.ats.AtsDtos.MatchResponse;
import com.jobsearch.core_api.ats.AtsDtos.PlatformScore;
import com.jobsearch.core_api.ats.AtsDtos.Suggestion;
import com.jobsearch.core_api.config.AppProperties;
import com.jobsearch.core_api.jobs.Job;
import com.jobsearch.core_api.jobs.JobHandler;
import com.jobsearch.core_api.jobs.JobOutcome;
import com.jobsearch.core_api.jobs.JobType;
import com.jobsearch.core_api.vacancy.VacancyRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Scores a queued resume against a vacancy via ATS Screener, stores the report as the job result
 * and, when the job names a vacancy, its average score as {@code vacancy.match_percent}.
 */
@Component
public class AtsMatchHandler implements JobHandler {

	private static final Logger log = LoggerFactory.getLogger(AtsMatchHandler.class);

	private final AtsScreenerClient screenerClient;
	private final VacancyRepository vacancyRepository;
	private final TransactionTemplate transactionTemplate;
	private final ObjectMapper objectMapper;
	private final Duration lease;

	public AtsMatchHandler(
			AtsScreenerClient screenerClient,
			VacancyRepository vacancyRepository,
			PlatformTransactionManager transactionManager,
			ObjectMapper objectMapper,
			AppProperties appProperties
	) {
		this.screenerClient = screenerClient;
		this.vacancyRepository = vacancyRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.objectMapper = objectMapper;
		// The screener call may take the whole timeout; the slack covers storing the result.
		this.lease = Duration.ofSeconds(appProperties.getAtsScreener().getTimeoutSeconds() + 30);
	}

	@Override
	public JobType type() {
		return JobType.ATS_MATCH;
	}

	/** Each match can hold a screener slot for minutes; keep the LLM bill and the screener load bounded. */
	@Override
	public int concurrency() {
		return 2;
	}

	@Override
	public int maxAttempts() {
		return 2;
	}

	@Override
	public Duration lease() {
		return lease;
	}

	@Override
	public JobOutcome run(Job job) {
		AtsMatchPayload payload = objectMapper.readValue(job.getPayload(), AtsMatchPayload.class);
		return switch (screenerClient.fullScore(payload.resumeText(), payload.vacancyText())) {
			case ScreenerResult.Report(JsonNode root) -> {
				MatchResponse report = mapResponse(root);
				if (payload.vacancyId() != null) {
					saveMatchPercent(job.getUserId(), payload.vacancyId(), report.averageScore());
				}
				log.info("ATS match scored jobId={} average={} platforms={} provider={} cached={}",
						job.getId(), report.averageScore(), report.platforms().size(), report.provider(), report.cached());
				yield new JobOutcome.Succeeded(objectMapper.writeValueAsString(report));
			}
			// Not retried: each try can take minutes and spends LLM tokens.
			case ScreenerResult.Failed(String message) -> new JobOutcome.Failed("ats_failed", message);
			case ScreenerResult.Unavailable(String reason) -> new JobOutcome.Retry(reason);
		};
	}

	/** Scoped to the job's owner: there is no logged-in user on the dispatcher thread. */
	private void saveMatchPercent(long userId, long vacancyId, int averageScore) {
		transactionTemplate.executeWithoutResult(status -> vacancyRepository.findByIdAndUserId(vacancyId, userId)
				.ifPresentOrElse(
						vacancy -> {
							vacancy.setMatchPercent(averageScore);
							vacancyRepository.save(vacancy);
						},
						() -> log.info("ATS match vacancy not found for job owner, matchPercent not saved vacancyId={} userId={}", vacancyId, userId)
				));
	}

	private static MatchResponse mapResponse(JsonNode root) {
		List<PlatformScore> platforms = new ArrayList<>();
		Map<String, Suggestion> suggestionsBySummary = new LinkedHashMap<>();

		JsonNode results = root.path("results");
		if (results.isArray()) {
			for (JsonNode item : results) {
				String system = item.path("system").asString("").strip();
				String vendor = item.path("vendor").asString("").strip();
				int score = clampScore(item.path("overallScore").asInt(0));
				boolean passes = item.path("passesFilter").asBoolean(false);
				if (!system.isBlank()) {
					platforms.add(new PlatformScore(system, vendor, score, passes));
				}

				JsonNode suggNode = item.path("suggestions");
				if (suggNode.isArray()) {
					for (JsonNode s : suggNode) {
						String summary = s.path("summary").asString("").strip();
						if (summary.isBlank()) {
							continue;
						}
						suggestionsBySummary.putIfAbsent(summary, new Suggestion(
								summary,
								stringList(s.path("details")),
								s.path("impact").asString("medium"),
								stringList(s.path("platforms"))
						));
					}
				}
			}
		}

		int average = platforms.isEmpty()
				? 0
				: (int) Math.round(platforms.stream().mapToInt(PlatformScore::overallScore).average().orElse(0));

		String provider = root.path("_provider").asString("");
		boolean cached = root.path("_cached").asBoolean(false);
		String summary = buildSummary(average, platforms);

		return new MatchResponse(
				average,
				List.copyOf(platforms),
				List.copyOf(suggestionsBySummary.values()),
				provider.isBlank() ? null : provider,
				cached,
				summary
		);
	}

	private static String buildSummary(int average, List<PlatformScore> platforms) {
		if (platforms.isEmpty()) {
			return "ATS Screener returned no platform scores.";
		}
		StringBuilder sb = new StringBuilder();
		sb.append(String.format(Locale.ROOT, "Average ATS match: %d/100 across %d platforms.", average, platforms.size()));
		for (PlatformScore p : platforms) {
			sb.append('\n')
					.append(p.system())
					.append(": ")
					.append(p.overallScore())
					.append(p.passesFilter() ? " (pass)" : " (fail)");
		}
		return sb.toString();
	}

	private static List<String> stringList(JsonNode node) {
		if (!node.isArray()) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (JsonNode n : node) {
			String v = n.asString("").strip();
			if (!v.isBlank()) {
				out.add(v);
			}
		}
		return List.copyOf(out);
	}

	private static int clampScore(int score) {
		return Math.max(0, Math.min(100, score));
	}
}
