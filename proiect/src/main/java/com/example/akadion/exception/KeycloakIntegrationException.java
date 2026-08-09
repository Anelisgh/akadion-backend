package com.example.akadion.exception;

// Această eroare este aruncată atunci când conexiunea sau apelul API dintre serverul nostru de backend și Keycloak eșuează.
// De exemplu: dacă serverul Keycloak este oprit sau rețeaua nu funcționează.
// Este mapată automat la codul HTTP 502 (Bad Gateway), semnalând că un serviciu extern de care depindem a dat eroare.
public class KeycloakIntegrationException extends RuntimeException {

    // Constructor simplu care transmite doar mesajul de eroare.
    public KeycloakIntegrationException(String message) {
        super(message);
    }

    // Constructor care permite salvarea erorii inițiale (cauza/root-cause) pentru depanare avansată (debugging).
    public KeycloakIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
