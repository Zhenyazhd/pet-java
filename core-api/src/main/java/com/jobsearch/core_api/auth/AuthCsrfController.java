package com.jobsearch.core_api.auth;

import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes CSRF token so the SPA can read/send X-XSRF-TOKEN before login. */
@RestController
@RequestMapping("/api/auth")
public class AuthCsrfController {

	@GetMapping("/csrf")
	public Map<String, String> csrf(CsrfToken token) {
		return Map.of(
				"headerName", token.getHeaderName(),
				"parameterName", token.getParameterName(),
				"token", token.getToken()
		);
	}
}
