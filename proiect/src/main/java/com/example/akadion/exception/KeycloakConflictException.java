package com.example.akadion.exception;

/**
 * Aruncată când Keycloak returnează 409 Conflict la crearea unui user
 * (email există deja direct în Keycloak, ocolind aplicația).
 * Necesită intervenție manuală în consola Keycloak — adminul NU trebuie să reîncerce orbește.
 * Mapată la HTTP 409 de GlobalExceptionHandler.
 */
public class KeycloakConflictException extends RuntimeException {

    public KeycloakConflictException(String mail) {
        super("Emailul '" + mail + "' există deja direct în Keycloak. " +
              "Necesită curățare manuală în consola Keycloak înainte de a reîncerca acceptarea.");
    }
}
