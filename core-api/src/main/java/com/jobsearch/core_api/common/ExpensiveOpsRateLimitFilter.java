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
 * and resume PDF compilation (each compile spawns a subprocess or container).
 */
public class ExpensiveOpsRateLimitFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(ExpensiveOpsRateLimitFilter.class);
	private static final long WINDOW_MS = TimeUnit.MINUTES.toMillis(1);
	private static final Set<String> LIMITED_EXACT_PATHS = Set.of(
			"/api/vacancies/import",
			"/api/resume/compile"
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
		String path = request.getRequestURI();
		return !path.startsWith("/api/ai/") && !LIMITED_EXACT_PATHS.contains(path);
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		int limit = appProperties.getExpensiveOpsRateLimit().getPerUserPerMinute();
		String userKey = currentUserKey();
		String key = "expensive|" + userKey;
		long retryAfter = rateLimiter.tryAcquire(key, limit, WINDOW_MS);
		if (retryAfter > 0) {
			log.warn("Expensive-ops rate limit hit path={} user={} retryAfter={}s",
					request.getRequestURI(), userKey, retryAfter);
			apiErrorResponses.write(
					response,
					HttpStatus.TOO_MANY_REQUESTS,
					"Too many AI/ATS/compile requests — try again later",
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
