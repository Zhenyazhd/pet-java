package com.jobsearch.core_api.auth;

import com.jobsearch.core_api.common.ApiErrorResponses;
import com.jobsearch.core_api.common.FixedWindowRateLimiter;
import com.jobsearch.core_api.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rate-limits POST /api/auth/login and /api/auth/register by client IP. */
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
		String path = request.getRequestURI();
		return !"/api/auth/login".equals(path) && !"/api/auth/register".equals(path);
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String path = request.getRequestURI();
		AppProperties.AuthRateLimit limits = appProperties.getAuthRateLimit();
		int limit = "/api/auth/login".equals(path)
				? limits.getLoginPerMinute()
				: limits.getRegisterPerMinute();
		String clientIp = clientIp(request, limits);
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

	/**
	 * Uses {@code remoteAddr} by default. Forwarded headers are honored only when
	 * {@code trust-forwarded-headers=true} <em>and</em> the peer is in {@code trusted-proxies}
	 * (so a direct client cannot spoof {@code X-Forwarded-For}).
	 */
	static String clientIp(HttpServletRequest request, AppProperties.AuthRateLimit limits) {
		String remote = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
		if (!limits.isTrustForwardedHeaders() || !isTrustedProxy(remote, limits.trustedProxyList())) {
			return remote;
		}
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].strip();
		}
		String realIp = request.getHeader("X-Real-IP");
		if (realIp != null && !realIp.isBlank()) {
			return realIp.strip();
		}
		return remote;
	}

	private static boolean isTrustedProxy(String remoteAddr, List<String> trustedProxies) {
		if (remoteAddr == null || remoteAddr.isBlank() || trustedProxies.isEmpty()) {
			return false;
		}
		for (String trusted : trustedProxies) {
			if (remoteAddr.equals(trusted) || remoteAddr.equals("[" + trusted + "]")) {
				return true;
			}
			// Tomcat may report IPv6 loopback as 0:0:0:0:0:0:0:1
			if ("::1".equals(trusted) && ("0:0:0:0:0:0:0:1".equals(remoteAddr) || "[0:0:0:0:0:0:0:1]".equals(remoteAddr))) {
				return true;
			}
		}
		return false;
	}
}
