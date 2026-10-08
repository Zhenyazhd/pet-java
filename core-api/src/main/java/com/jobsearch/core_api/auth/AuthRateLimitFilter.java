package com.jobsearch.core_api.auth;

import com.jobsearch.core_api.common.ApiErrorResponses;
import com.jobsearch.core_api.common.FixedWindowRateLimiter;
import com.jobsearch.core_api.common.RequestPaths;
import com.jobsearch.core_api.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthRateLimitFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);
	private static final long WINDOW_MS = TimeUnit.MINUTES.toMillis(1);

	private final FixedWindowRateLimiter rateLimiter;
	private final AppProperties appProperties;
	private final ApiErrorResponses apiErrorResponses;

	public AuthRateLimitFilter(
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
		return !"/api/auth/login".equals(path) && !"/api/auth/register".equals(path);
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String path = RequestPaths.routed(request);
		AppProperties.AuthRateLimit limits = appProperties.getAuthRateLimit();
		int limit = "/api/auth/login".equals(path)
				? limits.getLoginPerMinute()
				: limits.getRegisterPerMinute();
		String clientIp = clientIp(request);
		String key = path + "|" + clientIp;
		long retryAfter = rateLimiter.tryAcquire(key, limit, WINDOW_MS);
		if (retryAfter > 0) {
			log.warn("Auth rate limit hit path={} ip={} retryAfter={}s", path, clientIp, retryAfter);
			apiErrorResponses.write(
					response,
					HttpStatus.TOO_MANY_REQUESTS,
					"Too many requests — try again later",
					Map.of("Retry-After", String.valueOf(retryAfter))
			);
			return;
		}
		filterChain.doFilter(request, response);
	}

	static String clientIp(HttpServletRequest request) {
		return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
	}
}
