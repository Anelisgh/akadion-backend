package com.example.akadion.service;

import com.example.akadion.exception.KeycloakIntegrationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Integrare simplificată cu Keycloak Admin REST API.
 * Keycloak este acum doar IdP minim (stochează doar identitate + parolă).
 * DB-ul aplicației (app_user) stochează numele, prenumele și rolurile (singura sursă de adevăr).
 * Din Keycloak Admin API apelăm doar dezactivarea / reactivarea conturilor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakAdminService {

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final RestClient.Builder restClientBuilder;

    @Value("${app.keycloak.base-url}")
    private String keycloakBaseUrl;

    @Value("${app.keycloak.realm}")
    private String realm;

    /**
     * Dezactivează contul utilizatorului în Keycloak (enabled = false).
     */
    public void dezactiveazaUser(String idKeycloak) {
        updateEnabled(idKeycloak, false);
    }

    /**
     * Reactivează contul utilizatorului în Keycloak (enabled = true).
     */
    public void reactiveazaUser(String idKeycloak) {
        updateEnabled(idKeycloak, true);
    }

    private void updateEnabled(String idKeycloak, boolean enabled) {
        try {
            restClient().put()
                    .uri(keycloakBaseUrl + "/admin/realms/" + realm + "/users/" + idKeycloak)
                    .header("Authorization", "Bearer " + getAdminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("enabled", enabled))
                    .retrieve()
                    .toBodilessEntity();

            log.info("Keycloak: Contul utilizatorului sub={} a fost setat enabled={}", idKeycloak, enabled);

        } catch (RestClientException e) {
            throw new KeycloakIntegrationException(
                    "Eroare Keycloak la setarea enabled=" + enabled + " pentru sub=" + idKeycloak + ": " + e.getMessage(), e);
        }
    }

    /**
     * Obține token-ul service-account prin OAuth2AuthorizedClientManager (client_credentials).
     */
    private String getAdminToken() {
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId("keycloak-admin")
                .principal("service-account-keycloak-admin")
                .build();

        OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(authorizeRequest);
        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            throw new KeycloakIntegrationException(
                    "Nu s-a putut obține token-ul de service-account pentru clientul 'keycloak-admin'");
        }
        return authorizedClient.getAccessToken().getTokenValue();
    }

    private RestClient restClient() {
        return restClientBuilder.build();
    }
}
