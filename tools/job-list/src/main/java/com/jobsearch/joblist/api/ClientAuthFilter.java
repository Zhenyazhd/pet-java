package com.jobsearch.joblist.api;

import com.jobsearch.joblist.config.JobListProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Identifies the calling service from its bearer token and stores its name on the request. */
@Component
public class ClientAuthFilter extends OncePerRequestFilter {

	public static final String CLIENT = "job-list.client";
	private static final String BEARER = "Bearer ";

	private final JobListProperties properties;

	public ClientAuthFilter(JobListProperties properties) {
		this.properties = properties;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return request.getRequestURI().startsWith("/actuator/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Optional<String> client = clientFor(request.getHeader("Authorization"));
		if (client.isEmpty()) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType("application/json");
			response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"A valid bearer token is required\"}");
			return;
		}
		request.setAttribute(CLIENT, client.get());
		chain.doFilter(request, response);
	}

	/** Checks every client without stopping at the first hit, so the time does not reveal which token matched. */
	private Optional<String> clientFor(String header) {
		if (header == null || !header.startsWith(BEARER)) {
			return Optional.empty();
		}
		byte[] given = header.substring(BEARER.length()).getBytes(StandardCharsets.UTF_8);
		String found = null;
		for (JobListProperties.Client client : properties.clients()) {
			if (MessageDigest.isEqual(given, client.token().getBytes(StandardCharsets.UTF_8))) {
				found = client.name();
			}
		}
		return Optional.ofNullable(found);
	}
}
