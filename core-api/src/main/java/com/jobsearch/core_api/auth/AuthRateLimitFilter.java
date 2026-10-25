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
		return limitFor(RequestPaths.routed(request)) < 0;
	}

	/** Per client address and minute; the calls that send mail or spend a one-time link share the registration limit. */
	private int limitFor(String path) {
		AppProperties.AuthRateLimit limits = appProperties.getAuthRateLimit();
		return switch (path) {
			case "/api/auth/login" -> limits.getLoginPerMinute();
			case "/api/auth/register", "/api/auth/verify-email", "/api/auth/resend-verification",
					"/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/google/start" -> limits.getRegisterPerMinute();
			default -> -1;
		};
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String path = RequestPaths.routed(request);
		int limit = limitFor(path);
		String clientIp = clientIp(request);
		String key = path + "|" + clientIp;
		long retryAfter = rateLimiter.tryAcquire(key, limit, WINDOW_MS);
		if (retryAfter > 0) {
			log.warn("Auth rate limit hit path={} ip={} retryAfter={}s", path, clientIp, retryAfter);
			apiErrorResponses.writeTooManyRequests(response, "Too many requests — try again later", retryAfter);
			return;
		}
		filterChain.doFilter(request, response);
	}

	static String clientIp(HttpServletRequest request) {
		return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
	}
}
