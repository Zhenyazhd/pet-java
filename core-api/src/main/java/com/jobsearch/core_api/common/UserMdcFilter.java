package com.jobsearch.core_api.common;

import com.jobsearch.core_api.auth.AppUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class UserMdcFilter extends OncePerRequestFilter {

	static final String KEY = "userId";

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		AppUserPrincipal principal = AppUserPrincipal.current();
		if (principal != null) {
			MDC.put(KEY, String.valueOf(principal.getId()));
		}
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			MDC.remove(KEY);
		}
	}
}
