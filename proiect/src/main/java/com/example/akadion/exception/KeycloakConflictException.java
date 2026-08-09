package com.example.akadion.exception;

// Această eroare este aruncată atunci când încercăm să operăm acțiuni pe un email care este deja înregistrat în Keycloak.
// Reprezintă o stare de conflict (HTTP 409) între baza noastră de date și serverul de Keycloak.
// De exemplu, dacă un utilizator s-a înregistrat manual direct în consola Keycloak, ocolind fluxul normal.
public class KeycloakConflictException extends RuntimeException {

    // Constructorul generează un mesaj clar de eroare indicând că emailul duplicat necesită intervenția administratorului în Keycloak.
    public KeycloakConflictException(String mail) {
        super("Emailul '" + mail + "' există deja direct în Keycloak. " +
              "Necesită curățare manuală în consola Keycloak înainte de a reîncerca acceptarea.");
    }
}
