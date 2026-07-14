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
// Această clasă se ocupă de interacțiunea directă (prin API) cu serverul Keycloak.
// Deoarece decizia noastră este ca baza de date locală (DB) să fie singura sursă de adevăr pentru roluri și date,
// din Keycloak Admin API apelăm doar dezactivarea / reactivarea conturilor la nivel de rețea.
@Slf4j
@Service // Îi spune lui Spring că această clasă conține logică de business (serviciu)
@RequiredArgsConstructor
public class KeycloakAdminService {

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

    // Metodă privată ajutătoare care trimite o cerere HTTP PUT către Keycloak pentru a schimba parametrul "enabled".
    private void updateEnabled(String idKeycloak, boolean enabled) {
        try {
            // Trimitem cererea PUT către URL-ul specific utilizatorului din Keycloak:
            // "http://localhost:8080/admin/realms/akadion/users/{idKeycloak}"
            restClient().put()
                    .uri(keycloakBaseUrl + "/admin/realms/" + realm + "/users/" + idKeycloak)
                    // Atașăm în header token-ul de admin obținut prin getAdminToken()
                    .header("Authorization", "Bearer " + getAdminToken())
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
        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
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
}
