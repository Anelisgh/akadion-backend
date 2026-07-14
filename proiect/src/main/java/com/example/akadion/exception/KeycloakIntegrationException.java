package com.example.akadion.exception;

/**
 * Aruncată la orice eroare de comunicare cu Keycloak Admin API
 * (altele decât 409 Conflict).
 * Mapată la HTTP 502 Bad Gateway de GlobalExceptionHandler.
 */
public class KeycloakIntegrationException extends RuntimeException {

    public KeycloakIntegrationException(String message) {
        super(message);
    }

    public KeycloakIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
