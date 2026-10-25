package com.jobsearch.core_api.auth.google;

import com.jobsearch.core_api.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

@Configuration
@ConditionalOnExpression(GoogleOAuthConfig.ENABLED)
class GoogleOAuthConfig {

	/** The beans of sign-in with Google exist only when a client id is configured. */
	static final String ENABLED = "!'${app.google.client-id:}'.isBlank()";
	static final String REGISTRATION_ID = "google";

	@Bean
	ClientRegistrationRepository clientRegistrationRepository(AppProperties appProperties) {
		ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder(REGISTRATION_ID)
				.clientId(appProperties.getGoogle().getClientId())
				.clientSecret(appProperties.getGoogle().getClientSecret())
				.scope("openid", "email", "profile")
				// From the public address, not the request: behind a proxy the request's own address is the wrong one.
				.redirectUri(appProperties.getPublicBaseUrl().replaceAll("/+$", "") + "/login/oauth2/code/" + REGISTRATION_ID)
				.build();
		return new InMemoryClientRegistrationRepository(google);
	}
}
