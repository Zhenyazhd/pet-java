package com.jobsearch.core_api.auth;

import com.jobsearch.core_api.profile.AppUser;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Security principal backed by app_user. */
public class AppUserPrincipal implements UserDetails {

	private final Long id;
	private final String email;
	private final String displayName;
	private final String passwordHash;
	private final UserRole role;

	public AppUserPrincipal(Long id, String email, String displayName, String passwordHash, UserRole role) {
		this.id = id;
		this.email = email;
		this.displayName = displayName;
		this.passwordHash = passwordHash;
		this.role = role;
	}

	/** The signed-in user of the current request, or {@code null} when nobody is signed in. */
	public static AppUserPrincipal current() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal
				? principal
				: null;
	}

	public static AppUserPrincipal of(AppUser user) {
		return new AppUserPrincipal(
				user.getId(), user.getEmail(), user.getDisplayName(), user.getPasswordHash(), user.getRole());
	}

	public Long getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public UserRole getRole() {
		return role;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return passwordHash != null && !passwordHash.isBlank();
	}
}
