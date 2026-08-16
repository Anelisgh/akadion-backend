package com.example.akadion.auth.service;

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
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Integrare simplificată cu Keycloak Admin REST API.
 * Keycloak este acum doar IdP minim (stochează doar identitate + parolă).
 * DB-ul aplicației (app_user) stochează numele, prenumele și rolurile (singura sursă de adevăr).
 * Din Keycloak Admin API apelăm doar dezactivarea / reactivarea conturilor, la nivel de rețea.
 */
@Slf4j
@Service // Îi spune lui Spring că această clasă conține logică de business (serviciu)
@RequiredArgsConstructor
public class KeycloakAdminService {

    private static final String KEYCLOAK_REALM_USERS_PATH = "/admin/realms/";
    private static final String USERS_SEGMENT = "/users/";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    // Managerul configurat în OAuth2ClientConfig, care ne dă automat tokenul de admin.
    private final OAuth2AuthorizedClientManager authorizedClientManager;
    // Constructorul de clienți HTTP (RestClient) folosit pentru a trimite cereri PUT către Keycloak.
    private final RestClient.Builder restClientBuilder;

    @Value("${app.keycloak.base-url}")
    private String keycloakBaseUrl; // Adresa Keycloak (ex: http://localhost:8080)

    @Value("${app.keycloak.realm}")
    private String realm; // Numele domeniului/realm-ului din Keycloak (ex: 'akadion')

    // 1. Dezactivează contul în Keycloak (blochează utilizatorul să se mai poată loga).
    public void dezactiveazaUser(String idKeycloak) {
        updateEnabled(idKeycloak, false);
    }

    // 2. Activează la loc contul în Keycloak (permite logarea).
    public void reactiveazaUser(String idKeycloak) {
        updateEnabled(idKeycloak, true);
    }

    // 3. Actualizează email-ul în Keycloak
    public void updateEmail(String idKeycloak, String newEmail, boolean emailVerified) {
        try {
            restClient().put()
                    .uri(userUrl(idKeycloak))
                    .header(AUTHORIZATION_HEADER, BEARER_PREFIX + getAdminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "email", newEmail,
                            "username", newEmail,
                            "emailVerified", emailVerified
                    ))
                    .retrieve()
                    .toBodilessEntity();

            log.info("Keycloak: Email actualizat pentru sub={} la email={}, verificat={}", idKeycloak, newEmail, emailVerified);
        } catch (RestClientResponseException e) {
            log.error("Eroare răspuns Keycloak (status {}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new KeycloakIntegrationException(
                    "Eroare Keycloak [" + e.getStatusCode() + "]: " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new KeycloakIntegrationException(
                    "Eroare Keycloak la actualizarea email-ului pentru sub=" + idKeycloak + ": " + e.getMessage(), e);
        }
    }

    // 4. Execută acțiuni Keycloak pe email (UPDATE_PASSWORD, VERIFY_EMAIL)
    public void executeActionsEmail(String idKeycloak, java.util.List<String> actions, String clientId, String redirectUri) {
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(
                    userUrl(idKeycloak) + "/execute-actions-email");

            // Adăugăm query parameters dacă sunt prezenți
            if (clientId != null) {
                uriBuilder.queryParam("client_id", clientId);
                if (redirectUri != null) {
                    uriBuilder.queryParam("redirect_uri", redirectUri);
                }
            }

            restClient().put()
                    .uri(uriBuilder.build().toUri())
                    .header(AUTHORIZATION_HEADER, BEARER_PREFIX + getAdminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(actions) // Lista de acțiuni, ex: ["UPDATE_PASSWORD"] sau ["VERIFY_EMAIL"]
                    .retrieve()
                    .toBodilessEntity();

            log.info("Keycloak: Acțiuni email declanșate pentru sub={} - {}", idKeycloak, actions);
        } catch (RestClientException e) {
            throw new KeycloakIntegrationException(
                    "Eroare Keycloak la declanșarea acțiunilor de email pentru sub=" + idKeycloak + ": " + e.getMessage(), e);
        }
    }

    // Metodă privată ajutătoare care trimite o cerere HTTP PUT către Keycloak pentru a schimba parametrul "enabled".
    private void updateEnabled(String idKeycloak, boolean enabled) {
        try {
            // Trimitem cererea PUT către URL-ul specific utilizatorului din Keycloak:
            // "http://localhost:8080/admin/realms/akadion/users/{idKeycloak}"
            restClient().put()
                    .uri(userUrl(idKeycloak))
                    // Atașăm în header token-ul de admin obținut prin getAdminToken()
                    .header(AUTHORIZATION_HEADER, BEARER_PREFIX + getAdminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    // Trimitem în corpul cererii (JSON) parametrul {"enabled": true/false}
                    .body(Map.of("enabled", enabled))
                    .retrieve()
                    // Nu ne interesează răspunsul (corpul răspunsului e gol) - vrem doar să vedem dacă s-a executat cu succes
                    .toBodilessEntity();

            log.info("Keycloak: Contul utilizatorului sub={} a fost setat enabled={}", idKeycloak, enabled);

        } catch (RestClientException e) {
            // În caz că serverul Keycloak dă eroare, aruncăm eroarea noastră custom ca să fie prinsă în GlobalExceptionHandler.
            throw new KeycloakIntegrationException(
                    "Eroare Keycloak la setarea enabled=" + enabled + " pentru sub=" + idKeycloak + ": " + e.getMessage(), e);
        }
    }

    // Metodă privată care cere managerului OAuth2 tokenul curent de service-account (admin).
    private String getAdminToken() {
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId("keycloak-admin") // Corespunde cu înregistrarea din application.properties
                .principal("service-account-keycloak-admin")
                .build();

        // Managerul se ocupă în spate de tot: verificare cache, cerere token nou, etc.
        OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(authorizeRequest);
        if (authorizedClient == null) {
            throw new KeycloakIntegrationException(
                    "Nu s-a putut obține token-ul de service-account pentru clientul 'keycloak-admin'");
        }
        // Returnează valoarea text a tokenului de acces (ex: "eyJhbGciOiJSUzI1Ni...")
        return authorizedClient.getAccessToken().getTokenValue();
    }

    // Instanțiază clientul REST HTTP pe baza constructorului primit
    private RestClient restClient() {
        return restClientBuilder.build();
    }

    private String userUrl(String idKeycloak) {
        return keycloakBaseUrl + KEYCLOAK_REALM_USERS_PATH + realm + USERS_SEGMENT + idKeycloak;
    }
}
