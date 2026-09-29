package com.jobsearch.core_api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		csrfTokenRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").path("/"));

		http
				.csrf(csrf -> csrf
						.csrfTokenRepository(csrfTokenRepository)
						.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
				.securityContext(context -> context.securityContextRepository(securityContextRepository()))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
						.requestMatchers("/api/invite-codes", "/api/invite-codes/**").hasRole("ADMIN")
						.requestMatchers("/api/**").authenticated()
						.anyRequest().permitAll()
				)
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint((request, response, authException) ->
								writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
						.accessDeniedHandler((request, response, accessDeniedException) ->
								writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden"))
				)
				.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.logout(logout -> logout.disable());

		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(List.of("http://localhost:5173"));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		config.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}

	private static void writeJsonError(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		String error = status == 401 ? "Unauthorized" : "Forbidden";
		String body = """
				{"timestamp":"%s","status":%d,"error":"%s","message":"%s"}
				""".formatted(Instant.now(), status, error, message).trim();
		response.getWriter().write(body);
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
