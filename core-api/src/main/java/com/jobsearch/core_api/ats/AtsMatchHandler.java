package com.jobsearch.core_api.ats;

import com.jobsearch.core_api.ats.AtsDtos.MatchResponse;
import com.jobsearch.core_api.ats.AtsDtos.PlatformScore;
import com.jobsearch.core_api.ats.AtsDtos.Suggestion;
import com.jobsearch.core_api.compile.ResumePdfCache;
import com.jobsearch.core_api.compile.ResumePdfCacheRepository;
import com.jobsearch.core_api.config.AppProperties;
import com.jobsearch.core_api.jobs.Job;
import com.jobsearch.core_api.jobs.JobHandler;
import com.jobsearch.core_api.jobs.JobOutcome;
import com.jobsearch.core_api.jobs.JobType;
import com.jobsearch.core_api.vacancy.VacancyRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Scores a built resume PDF against a vacancy via the ats-worker (the model reads the text an ATS extracts from the PDF), stores the report as the job result
 * and, when the job names a vacancy, its average score as {@code vacancy.match_percent}.
 */
@Component
public class AtsMatchHandler implements JobHandler {

	private static final Logger log = LoggerFactory.getLogger(AtsMatchHandler.class);
	private static final int MAX_SUGGESTIONS = 10;
	private static final int MAX_LIST_ITEMS = 8;
	private static final int MAX_TEXT_CHARS = 1000;

	private final AtsScoreClient scoreClient;
	private final ResumePdfCacheRepository cacheRepository;
	private final VacancyRepository vacancyRepository;
	private final ObjectMapper objectMapper;
	private final Duration lease;
	private final boolean enabled;

	public AtsMatchHandler(
			AtsScoreClient scoreClient,
			ResumePdfCacheRepository cacheRepository,
			VacancyRepository vacancyRepository,
			ObjectMapper objectMapper,
			AppProperties appProperties
	) {
		this.scoreClient = scoreClient;
		this.cacheRepository = cacheRepository;
		this.vacancyRepository = vacancyRepository;
		this.objectMapper = objectMapper;
		// The worker call may take the whole timeout; the slack covers storing the result.
		this.lease = Duration.ofSeconds(appProperties.getAtsWorker().getScoreTimeoutSeconds() + 30);
		this.enabled = appProperties.getAtsWorker().isEnabled();
	}

	@Override
	public boolean disabled() {
		return !enabled;
	}

	@Override
	public JobType type() {
		return JobType.ATS_MATCH;
	}

	/** Each match is a model call that can run for a minute and costs tokens; keep the LLM bill bounded. */
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
		AtsMatchPayload payload;
		try {
			payload = objectMapper.readValue(job.getPayload(), AtsMatchPayload.class);
		}
		catch (JacksonException ex) {
			log.error("ATS match payload unreadable jobId={}", job.getId(), ex);
			return new JobOutcome.Failed("bad_payload", "This request could not be processed.");
		}
		byte[] pdf = cacheRepository.findById(payload.sourceHash()).map(ResumePdfCache::getPdf).orElse(null);
		if (pdf == null) {
			// Cached PDFs unused for 30 days are deleted by PdfCacheJanitor.
			return new JobOutcome.Failed("pdf_expired", "The PDF has expired. Build the PDF again.");
		}
		return switch (scoreClient.fullScore(pdf, payload.vacancyText())) {
			case ScoreResult.Report(JsonNode root) -> {
				MatchResponse report = mapResponse(root);
				if (report.platforms().isEmpty()) {
					log.error("ATS match answer has no usable platform scores jobId={}", job.getId());
					yield new JobOutcome.Failed("ats_failed", "The ATS score could not be computed. Please try again later.");
				}
				if (payload.vacancyId() != null) {
					saveMatchPercent(job.getUserId(), payload.vacancyId(), report.averageScore());
				}
				log.info("ATS match scored jobId={} average={} platforms={} provider={} cached={}",
						job.getId(), report.averageScore(), report.platforms().size(), report.provider(), report.cached());
				yield new JobOutcome.Succeeded(objectMapper.writeValueAsString(report));
			}
			// Not retried: each try can take minutes and spends LLM tokens.
			case ScoreResult.Failed(String message) -> new JobOutcome.Failed("ats_failed", message);
			case ScoreResult.Busy(long retryAfterSeconds) -> new JobOutcome.Busy(Duration.ofSeconds(retryAfterSeconds));
			case ScoreResult.Unavailable(String reason) -> new JobOutcome.Retry(reason);
		};
	}

	/** Scoped to the job's owner: there is no logged-in user on the dispatcher thread. */
	private void saveMatchPercent(long userId, long vacancyId, int averageScore) {
		try {
			if (vacancyRepository.updateMatchPercent(vacancyId, userId, averageScore, Instant.now()) == 0) {
				log.info("ATS match vacancy not found for job owner, matchPercent not saved vacancyId={} userId={}", vacancyId, userId);
			}
		}
		catch (RuntimeException ex) {
			log.error("ATS match percent not saved vacancyId={} userId={}", vacancyId, userId, ex);
		}
	}

	private static MatchResponse mapResponse(JsonNode root) {
		List<PlatformScore> platforms = new ArrayList<>();
		Map<String, Suggestion> suggestionsBySummary = new LinkedHashMap<>();

		JsonNode results = root.path("results");
		if (results.isArray()) {
			for (JsonNode item : results) {
				String system = item.path("system").asString("").strip();
				String vendor = item.path("vendor").asString("").strip();
				boolean passes = item.path("passesFilter").asBoolean(false);
				if (!system.isBlank() && item.path("overallScore").isNumber()) {
					int score = clampScore(item.path("overallScore").asInt());
					platforms.add(new PlatformScore(system, vendor, score, passes));
				}

				JsonNode suggNode = item.path("suggestions");
				if (suggNode.isArray()) {
					for (JsonNode s : suggNode) {
						String summary = cut(s.path("summary").asString("").strip());
						if (summary.isBlank() || suggestionsBySummary.size() >= MAX_SUGGESTIONS) {
							continue;
						}
						suggestionsBySummary.putIfAbsent(summary, new Suggestion(
								summary,
								stringList(s.path("details")),
								cut(s.path("impact").asString("medium")),
								stringList(s.path("platforms"))
						));
					}
				}
			}
		}

		int average = platforms.isEmpty()
				? 0
				: (int) Math.round(platforms.stream().mapToInt(PlatformScore::overallScore).average().orElse(0));

		String provider = root.path("_model").asString("");
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
			return "The model returned no platform scores.";
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
			String v = cut(n.asString("").strip());
			if (!v.isBlank() && out.size() < MAX_LIST_ITEMS) {
				out.add(v);
			}
		}
		return List.copyOf(out);
	}

	/** The model's text goes into the stored job result: bound what one answer can add to it. */
	private static String cut(String text) {
		return text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
	}

	private static int clampScore(int score) {
		return Math.max(0, Math.min(100, score));
	}
}
