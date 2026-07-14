package com.example.akadion.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Personalizează request-ul de autorizare OAuth2.
 * Dacă request-ul se face pe "/oauth2/authorization/keycloak-register",
 * schimbă endpoint-ul din Keycloak din "/auth" în "/registrations",
 * trimițând utilizatorul direct către pagina de înregistrare nativă Keycloak.
 */
// Această clasă se ocupă de personalizarea modului în care serverul nostru pornește legătura cu Keycloak.
// În special, ne ajută să avem un buton de "Creează cont" în React care trimite utilizatorul direct la pagina
// de înregistrare nativă Keycloak (fără să îl trimită întâi pe pagina de login standard).
public class CustomAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
    private final OAuth2AuthorizationRequestResolver defaultResolver;

    public CustomAuthorizationRequestResolver(ClientRegistrationRepository repo) {
        // Pasul 1: Creăm rezolvatorul standard oferit de Spring pentru căile "/oauth2/authorization"
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
        
        // Pasul 2: Activăm manual protecția de tip PKCE (S256).
        // Aceasta este o măsură importantă de securitate împotriva interceptării codurilor de login (Keycloak o cere obligatoriu).
        resolver.setAuthorizationRequestCustomizer(org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers.withPkce());
        
        this.defaultResolver = resolver;
    }

    // Apel pentru cereri standard (fără specificarea unui ID de înregistrare în URL)
    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return customize(defaultResolver.resolve(request), request);
    }

    // Apel pentru cereri specifice (când link-ul conține ID-ul clientului OAuth2)
    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        return customize(defaultResolver.resolve(request, registrationId), request);
    }

    // Metodă personalizată care modifică link-ul final de redirecționare către Keycloak.
    private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest req, HttpServletRequest request) {
        if (req == null) return null;
        
        // Pasul 3: Dacă utilizatorul a accesat link-ul special pentru înregistrare (/keycloak-register)
        if (request.getRequestURI().endsWith("/keycloak-register")) {
            // Modificăm calea din URL-ul Keycloak: înlocuim "/auth" (pagina de login) cu "/registrations" (pagina de înregistrare).
            String registerUri = req.getAuthorizationUri().replace("/auth", "/registrations");
            
            // Reconstruim cererea de redirecționare cu noul URL modificat.
            return OAuth2AuthorizationRequest.from(req).authorizationRequestUri(registerUri).build();
        }
        
        // Pentru restul căilor (ex: login normal), lăsăm link-ul nemodificat.
        return req;
    }
}
