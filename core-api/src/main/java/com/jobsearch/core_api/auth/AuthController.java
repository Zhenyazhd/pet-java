package com.jobsearch.core_api.auth;

import com.jobsearch.core_api.auth.account.AccountEmailService;
import com.jobsearch.core_api.auth.google.GoogleLoginHandler;
import com.jobsearch.core_api.auth.invite.InviteCodeService;
import com.jobsearch.core_api.auth.AuthDtos.AuthUserResponse;
import com.jobsearch.core_api.auth.AuthDtos.DeleteAccountRequest;
import com.jobsearch.core_api.auth.AuthDtos.EmailRequest;
import com.jobsearch.core_api.auth.AuthDtos.InviteRequest;
import com.jobsearch.core_api.auth.AuthDtos.PublicConfig;
import com.jobsearch.core_api.auth.AuthDtos.LoginRequest;
import com.jobsearch.core_api.auth.AuthDtos.RegisterRequest;
import com.jobsearch.core_api.auth.AuthDtos.Registered;
import com.jobsearch.core_api.auth.AuthDtos.ResetPasswordRequest;
import com.jobsearch.core_api.auth.AuthDtos.TokenRequest;
import com.jobsearch.core_api.common.NotFoundException;
import com.jobsearch.core_api.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final AccountEmailService accountEmailService;
	private final InviteCodeService inviteCodeService;
	private final PublicConfig publicConfig;

	public AuthController(
			AuthService authService,
			AccountEmailService accountEmailService,
			InviteCodeService inviteCodeService,
			AppProperties appProperties
	) {
		this.authService = authService;
		this.accountEmailService = accountEmailService;
		this.inviteCodeService = inviteCodeService;
		this.publicConfig = new PublicConfig(appProperties.getGoogle().isEnabled());
	}

	/** Mints the XSRF-TOKEN cookie so the SPA can send X-XSRF-TOKEN before login. */
	@GetMapping("/csrf")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void csrf(CsrfToken token) {
		token.getToken();
	}

	@GetMapping("/config")
	public PublicConfig config() {
		return publicConfig;
	}

	@GetMapping("/me")
	public AuthUserResponse me() {
		return authService.me();
	}

	@PostMapping("/login")
	public AuthUserResponse login(
			@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse
	) {
		return authService.login(request, httpRequest, httpResponse);
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public Registered register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	/**
	 * A new person gives their invite code before going to Google. It is checked now, so a wrong one is told at once,
	 * and kept in the session until they come back; it is used (and spent) only if they turn out to be new.
	 */
	@PostMapping("/google/start")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void googleStart(@Valid @RequestBody InviteRequest request, HttpServletRequest httpRequest) {
		if (!publicConfig.google()) {
			throw new NotFoundException("Sign-in with Google is not switched on");
		}
		inviteCodeService.requireAvailable(request.inviteCode());
		httpRequest.getSession().setAttribute(GoogleLoginHandler.INVITE_ATTRIBUTE, request.inviteCode().strip());
	}

	@PostMapping("/verify-email")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void verifyEmail(@Valid @RequestBody TokenRequest request) {
		accountEmailService.verify(request.token());
	}

	/** Always 202, whether or not the address has an account. */
	@PostMapping("/resend-verification")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void resendVerification(@Valid @RequestBody EmailRequest request) {
		accountEmailService.resendVerification(request.email());
	}

	/** Always 202, whether or not the address has an account. */
	@PostMapping("/forgot-password")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void forgotPassword(@Valid @RequestBody EmailRequest request) {
		accountEmailService.forgotPassword(request.email());
	}

	@PostMapping("/reset-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		accountEmailService.resetPassword(request.token(), request.password());
	}

	@DeleteMapping("/account")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteAccount(
			@Valid @RequestBody DeleteAccountRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse
	) {
		authService.deleteAccount(request, httpRequest, httpResponse);
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		authService.logout(httpRequest, httpResponse);
	}
}
