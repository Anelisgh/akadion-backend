package com.example.akadion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

// Această clasă configurează un manager pentru autentificarea automată a backend-ului în Keycloak.
// Pentru ca backend-ul să poată apela comenzi administrative în Keycloak (cum ar fi dezactivarea unui utilizator),
// el are nevoie de un "token de acces" de administrator.
// În loc să facem cereri manuale HTTP pentru logarea backend-ului, lăsăm Spring Security să se ocupe automat de asta.
@Configuration
public class OAuth2ClientConfig {

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {

        // Pasul 1: Specificăm că folosim fluxul "Client Credentials" (autentificare pe bază de aplicație-la-aplicație,
        // fără ca un utilizator uman să introducă user/parolă).
        OAuth2AuthorizedClientProvider clientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .build();

        // Pasul 2: Creăm managerul care va stoca și reînnoi tokenul în siguranță.
        // Când codul nostru cere un token de admin, acest manager verifică dacă cel existent în cache mai este valabil.
        // Dacă a expirat, el cere automat altul de la Keycloak, fără ca noi să scriem cod manual de reîncercare.
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(clientProvider);
        return manager;
    }
}
