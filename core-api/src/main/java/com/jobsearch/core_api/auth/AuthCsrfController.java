package com.jobsearch.core_api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Mints the XSRF-TOKEN cookie so the SPA can send X-XSRF-TOKEN before login. */
@RestController
@RequestMapping("/api/auth")
public class AuthCsrfController {

	@GetMapping("/csrf")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void csrf(CsrfToken token) {
		token.getToken();
	}
}
