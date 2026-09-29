package com.jobsearch.core_api.auth;

import com.jobsearch.core_api.auth.AuthDtos.AuthUserResponse;
import com.jobsearch.core_api.auth.AuthDtos.LoginRequest;
import com.jobsearch.core_api.auth.AuthDtos.RegisterRequest;
import com.jobsearch.core_api.common.ConflictException;
import com.jobsearch.core_api.common.UnauthorizedException;
import com.jobsearch.core_api.profile.AppUser;
import com.jobsearch.core_api.profile.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final AppUserRepository appUserRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final InviteCodeService inviteCodeService;
	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

	public AuthService(
			AppUserRepository appUserRepository,
			PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager,
			InviteCodeService inviteCodeService
	) {
		this.appUserRepository = appUserRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.inviteCodeService = inviteCodeService;
	}

	@Transactional(readOnly = true)
	public AuthUserResponse me() {
		AppUserPrincipal principal = currentPrincipalOrThrow();
		return toResponse(principal);
	}

	@Transactional(readOnly = true)
	public AuthUserResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		try {
			Authentication authentication = authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(
							request.email().strip().toLowerCase(),
							request.password()
					)
			);
			establishSession(authentication, httpRequest, httpResponse);
			AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
			log.info("User logged in userId={}", principal.getId());
			return toResponse(principal);
		}
		catch (BadCredentialsException | DisabledException ex) {
			throw new UnauthorizedException("Invalid email or password");
		}
	}

	@Transactional
	public AuthUserResponse register(
			RegisterRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse
	) {
		String email = request.email().strip().toLowerCase();
		if (appUserRepository.existsByEmailIgnoreCase(email)) {
			throw new ConflictException("Email already registered");
		}

		String displayName = request.displayName() == null || request.displayName().isBlank()
				? email.split("@")[0]
				: request.displayName().strip();

		AppUser user = new AppUser();
		user.setEmail(email);
		user.setDisplayName(displayName);
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setCareerPath("");
		user.setResumeJson("");
		user.setRole(UserRole.USER.name());
		AppUser saved = appUserRepository.saveAndFlush(user);

		inviteCodeService.consume(request.inviteCode(), saved.getId());

		AppUserPrincipal principal = new AppUserPrincipal(
				saved.getId(),
				saved.getEmail(),
				saved.getDisplayName(),
				saved.getPasswordHash(),
				UserRole.USER
		);
		Authentication authentication = new UsernamePasswordAuthenticationToken(
				principal,
				null,
				principal.getAuthorities()
		);
		establishSession(authentication, httpRequest, httpResponse);
		log.info("Registered userId={} email={}", saved.getId(), saved.getEmail());
		return new AuthUserResponse(saved.getId(), saved.getEmail(), saved.getDisplayName(), UserRole.USER.name());
	}

	public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		SecurityContextHolder.clearContext();
		HttpSession session = httpRequest.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		securityContextRepository.saveContext(SecurityContextHolder.createEmptyContext(), httpRequest, httpResponse);
		log.info("User logged out");
	}

	private void establishSession(
			Authentication authentication,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse
	) {
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, httpRequest, httpResponse);
	}

	private AppUserPrincipal currentPrincipalOrThrow() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
			throw new UnauthorizedException("Not authenticated");
		}
		return principal;
	}

	private AuthUserResponse toResponse(AppUserPrincipal principal) {
		return new AuthUserResponse(
				principal.getId(),
				principal.getUsername(),
				principal.getDisplayName(),
				principal.getRole().name()
		);
	}
}
