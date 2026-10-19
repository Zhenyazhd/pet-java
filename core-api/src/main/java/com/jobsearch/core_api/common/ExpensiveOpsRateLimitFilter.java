package com.jobsearch.core_api.common;

import com.jobsearch.core_api.auth.AppUserPrincipal;
import com.jobsearch.core_api.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-user rate limit for expensive operations: AI suggest, ATS match, vacancy import,
 * starting a job search run or a rank-only run (both cost real money, one shared bucket),
 * queuing a resume PDF compile (each one occupies a pdf-worker slot) and a PDF readability check.
 */
public class ExpensiveOpsRateLimitFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(ExpensiveOpsRateLimitFilter.class);
	private static final int SEEN_FACTOR = 6;
	private static final long WINDOW_MS = TimeUnit.MINUTES.toMillis(1);
	private static final Set<String> LIMITED_EXACT_PATHS = Set.of(
			"/api/vacancies/import",
			"/api/resume/compile",
			"/api/resume/readability",
			"/api/job-search/runs",
			"/api/job-search/runs/rank"
	);

	private final FixedWindowRateLimiter rateLimiter;
	private final AppProperties appProperties;
	private final ApiErrorResponses apiErrorResponses;

	public ExpensiveOpsRateLimitFilter(
			FixedWindowRateLimiter rateLimiter,
			AppProperties appProperties,
			ApiErrorResponses apiErrorResponses
	) {
		this.rateLimiter = rateLimiter;
		this.appProperties = appProperties;
		this.apiErrorResponses = apiErrorResponses;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		if (!HttpMethod.POST.matches(request.getMethod())) {
			return true;
		}
		String path = RequestPaths.routed(request);
		return !path.startsWith("/api/ai/") && !LIMITED_EXACT_PATHS.contains(path) && !isSeen(path);
	}

	/** POST /api/job-search/matches/{id}/seen: each call walks up to several job-list pages, so it has a bucket of its own. */
	private static boolean isSeen(String path) {
		return path.startsWith("/api/job-search/matches/") && path.endsWith("/seen");
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		int base = appProperties.getExpensiveOpsRateLimit().getPerUserPerMinute();
		boolean seen = isSeen(RequestPaths.routed(request));
		// "seen" is cheaper than a run or an AI call, so its separate per-user bucket is 6 times as large (0 = off).
		int limit = seen ? base * SEEN_FACTOR : base;
		String userKey = currentUserKey();
		String key = (seen ? "expensive-seen|" : "expensive|") + userKey;
		long retryAfter = rateLimiter.tryAcquire(key, limit, WINDOW_MS);
		if (retryAfter > 0) {
			if (SecurityContextHolder.getContext().getAuthentication() != null
					&& SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof AppUserPrincipal) {
				log.warn("Expensive-ops rate limit hit path={} user={} retryAfter={}s",
						RequestPaths.routed(request), userKey, retryAfter);
			} else {
				log.debug("Expensive-ops rate limit hit path={} user={} retryAfter={}s",
						RequestPaths.routed(request), userKey, retryAfter);
			}
			apiErrorResponses.write(
					response,
					HttpStatus.TOO_MANY_REQUESTS,
					"Too many expensive requests (AI, import, PDF, job search). Try again in a moment.",
					Map.of("Retry-After", String.valueOf(retryAfter))
			);
			return;
		}
		filterChain.doFilter(request, response);
	}

	private static String currentUserKey() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
			return "user:" + principal.getId();
		}
		return "anon";
	}
}
