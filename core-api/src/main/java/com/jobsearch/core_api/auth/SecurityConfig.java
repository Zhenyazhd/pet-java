package com.jobsearch.core_api.auth;

import com.jobsearch.core_api.common.ApiErrorResponses;
import com.jobsearch.core_api.common.ExpensiveOpsRateLimitFilter;
import com.jobsearch.core_api.common.UserMdcFilter;
import com.jobsearch.core_api.common.FixedWindowRateLimiter;
import com.jobsearch.core_api.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	AuthRateLimitFilter authRateLimitFilter(
			FixedWindowRateLimiter rateLimiter,
			AppProperties appProperties,
			ApiErrorResponses apiErrorResponses
	) {
		return new AuthRateLimitFilter(rateLimiter, appProperties, apiErrorResponses);
	}

	@Bean
	ExpensiveOpsRateLimitFilter expensiveOpsRateLimitFilter(
			FixedWindowRateLimiter rateLimiter,
			AppProperties appProperties,
			ApiErrorResponses apiErrorResponses
	) {
		return new ExpensiveOpsRateLimitFilter(rateLimiter, appProperties, apiErrorResponses);
	}

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			AuthRateLimitFilter authRateLimitFilter,
			ExpensiveOpsRateLimitFilter expensiveOpsRateLimitFilter,
			ApiErrorResponses apiErrorResponses
	) throws Exception {
		CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		// Secure: readable by JS (SPA needs the token) but never sent over plain HTTP.
		csrfTokenRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").path("/").secure(true));

		http
				.csrf(csrf -> csrf
						.csrfTokenRepository(csrfTokenRepository)
						.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
				.securityContext(context -> context.securityContextRepository(securityContextRepository()))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
						// permitAll so expired sessions can still clear cookies; CSRF header is still required.
						.requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
						.requestMatchers("/api/invite-codes", "/api/invite-codes/**").hasRole("ADMIN")
						.requestMatchers("/api/**").authenticated()
						.anyRequest().permitAll()
				)
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint((request, response, authException) ->
								apiErrorResponses.write(response, HttpStatus.UNAUTHORIZED, "Unauthorized"))
						.accessDeniedHandler((request, response, accessDeniedException) ->
								apiErrorResponses.write(response, HttpStatus.FORBIDDEN, "Forbidden"))
				)
				.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
				.addFilterAfter(new UserMdcFilter(), CsrfCookieFilter.class)
				.addFilterAfter(authRateLimitFilter, UserMdcFilter.class)
				.addFilterAfter(expensiveOpsRateLimitFilter, AuthRateLimitFilter.class)
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.logout(logout -> logout.disable());

		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(AppProperties appProperties) {
		List<String> origins = appProperties.getCors().allowedOriginList();
		if (origins.isEmpty()) {
			throw new IllegalStateException("app.cors.allowed-origins must list at least one origin");
		}
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(origins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		config.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}

	/**
	 * SPA-friendly CSRF: prefer header token, fall back to XOR/BREACH-safe form token handling.
	 */
	static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
		private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
		private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
			xor.handle(request, response, csrfToken);
			csrfToken.get();
		}

		@Override
		public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
			String headerValue = request.getHeader(csrfToken.getHeaderName());
			return StringUtils.hasText(headerValue)
					? plain.resolveCsrfTokenValue(request, csrfToken)
					: xor.resolveCsrfTokenValue(request, csrfToken);
		}
	}

	/** Forces the CSRF cookie to be written on every request (needed for SPAs). */
	static final class CsrfCookieFilter extends OncePerRequestFilter {
		@Override
		protected void doFilterInternal(
				HttpServletRequest request,
				HttpServletResponse response,
				FilterChain filterChain
		) throws ServletException, IOException {
			CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
			if (csrfToken != null) {
				csrfToken.getToken();
			}
			filterChain.doFilter(request, response);
		}
	}
}
