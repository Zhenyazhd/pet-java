package com.jobsearch.core_api.ats;

import com.jobsearch.core_api.ats.AtsDtos.MatchRequest;
import com.jobsearch.core_api.ats.AtsDtos.MatchResponse;
import com.jobsearch.core_api.ats.AtsDtos.PlatformScore;
import com.jobsearch.core_api.ats.AtsDtos.Suggestion;
import com.jobsearch.core_api.auth.CurrentUserService;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.resume.ResumeDtos.ResumeDocument;
import com.jobsearch.core_api.resume.ResumePlainTextRenderer;
import com.jobsearch.core_api.resume.ResumeService;
import com.jobsearch.core_api.vacancy.Vacancy;
import com.jobsearch.core_api.vacancy.VacancyRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Scores the current resume against a vacancy via ATS Screener.
 * HTTP to the screener runs outside a DB transaction so the pool connection is not held open.
 */
@Service
public class AtsMatchService {

	private static final Logger log = LoggerFactory.getLogger(AtsMatchService.class);

	private final ResumeService resumeService;
	private final ResumePlainTextRenderer plainTextRenderer;
	private final AtsScreenerClient atsScreenerClient;
	private final VacancyRepository vacancyRepository;
	private final CurrentUserService currentUserService;
	private final TransactionTemplate transactionTemplate;

	public AtsMatchService(
			ResumeService resumeService,
			ResumePlainTextRenderer plainTextRenderer,
			AtsScreenerClient atsScreenerClient,
			VacancyRepository vacancyRepository,
			CurrentUserService currentUserService,
			PlatformTransactionManager transactionManager
	) {
		this.resumeService = resumeService;
		this.plainTextRenderer = plainTextRenderer;
		this.atsScreenerClient = atsScreenerClient;
		this.vacancyRepository = vacancyRepository;
		this.currentUserService = currentUserService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public MatchResponse match(MatchRequest request) {
		String vacancy = request.vacancyContext() == null ? "" : request.vacancyContext().strip();
		if (vacancy.isBlank()) {
			throw new IllegalArgumentException("vacancyContext is required");
		}
		if (vacancy.length() > 20_000) {
			throw new IllegalArgumentException("vacancyContext exceeds 20,000 characters");
		}

		// ResumeService.get() uses its own short read-only TX; do not wrap this method.
		ResumeDocument resume = resumeService.get();
		String resumeText = plainTextRenderer.render(resume);
		if (resumeText.isBlank()) {
			throw new IllegalArgumentException("Resume is empty — fill the sheet before matching");
		}

		JsonNode root = atsScreenerClient.fullScore(resumeText, vacancy);
		MatchResponse response = mapResponse(root);
		if (request.vacancyId() != null) {
			persistMatchPercent(request.vacancyId(), response.averageScore());
		}
		log.info(
				"ATS match done average={} platforms={} provider={} cached={} vacancyId={}",
				response.averageScore(),
				response.platforms().size(),
				response.provider(),
				response.cached(),
				request.vacancyId()
		);
		return response;
	}

	private void persistMatchPercent(Long vacancyId, int averageScore) {
		long userId = currentUserService.requireUserId();
		transactionTemplate.executeWithoutResult(status -> {
			Vacancy owned = vacancyRepository.findByIdAndUserId(vacancyId, userId)
					.orElseThrow(() -> new NotFoundException("Vacancy not found: " + vacancyId));
			owned.setMatchPercent(clampScore(averageScore));
			vacancyRepository.save(owned);
			log.info("Persisted matchPercent={} on vacancyId={} userId={}", averageScore, vacancyId, userId);
		});
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
