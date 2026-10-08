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

	static String clientIp(HttpServletRequest request, AppProperties.AuthRateLimit limits) {
		String remote = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
		List<String> trustedProxies = limits.trustedProxyList();
		if (!limits.isTrustForwardedHeaders() || !isTrustedProxy(remote, trustedProxies)) {
			return remote;
		}
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded == null || forwarded.isBlank()) {
			return remote;
		}
		String leftmostTrusted = remote;
		String[] hops = forwarded.split(",");
		for (int i = hops.length - 1; i >= 0; i--) {
			String hop = hops[i].strip();
			if (hop.isEmpty()) {
				continue;
			}
			if (!isTrustedProxy(hop, trustedProxies)) {
				return hop;
			}
			leftmostTrusted = hop;
		}
		return leftmostTrusted;
	}

	private static boolean isTrustedProxy(String address, List<String> trustedProxies) {
		if (trustedProxies.isEmpty()) {
			return false;
		}
		for (String trusted : trustedProxies) {
			if (address.equals(trusted) || address.equals("[" + trusted + "]")) {
				return true;
			}
			// Tomcat may report IPv6 loopback as 0:0:0:0:0:0:0:1
			if ("::1".equals(trusted) && ("0:0:0:0:0:0:0:1".equals(address) || "[0:0:0:0:0:0:0:1]".equals(address))) {
				return true;
			}
		}
		return false;
	}
}
