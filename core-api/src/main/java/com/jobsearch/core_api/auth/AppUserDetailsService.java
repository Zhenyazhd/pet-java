package com.jobsearch.core_api.auth;

import com.jobsearch.core_api.profile.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

	private final AppUserRepository appUserRepository;

	public AppUserDetailsService(AppUserRepository appUserRepository) {
		this.appUserRepository = appUserRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		return appUserRepository.findByEmailIgnoreCase(username.strip())
				.map(user -> new AppUserPrincipal(
						user.getId(),
						user.getEmail(),
						user.getDisplayName(),
						user.getPasswordHash(),
						parseRole(user.getRole())
				))
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
	}

	static UserRole parseRole(String role) {
		if (role == null || role.isBlank()) {
			return UserRole.USER;
		}
		try {
			return UserRole.valueOf(role.strip().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			return UserRole.USER;
		}
	}
}
