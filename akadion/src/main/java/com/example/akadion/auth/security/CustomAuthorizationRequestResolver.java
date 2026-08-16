package com.example.akadion.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Personalizează request-ul de autorizare OAuth2 pentru fluxul de înregistrare.
 */
public class CustomAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
    private static final String REGISTRATION_PATH = "/oauth2/authorization";
    private static final String REGISTER_REGISTRATION_ID = "keycloak-register";

    private final OAuth2AuthorizationRequestResolver defaultResolver;

    public CustomAuthorizationRequestResolver(ClientRegistrationRepository repo) {
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(repo, REGISTRATION_PATH);
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

        this.defaultResolver = resolver;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return customize(defaultResolver.resolve(request), requestRegistrationId(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        return customize(defaultResolver.resolve(request, registrationId), registrationId);
    }

    private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest req, String registrationId) {
        if (req == null) {
            return null;
        }

        if (REGISTER_REGISTRATION_ID.equals(registrationId)) {
            return OAuth2AuthorizationRequest.from(req)
                    .additionalParameters(params -> params.put("prompt", "create"))
                    .build();
        }

        return req;
    }

    private String requestRegistrationId(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String prefix = REGISTRATION_PATH + "/";
        return uri.startsWith(prefix) ? uri.substring(prefix.length()) : null;
    }
}
