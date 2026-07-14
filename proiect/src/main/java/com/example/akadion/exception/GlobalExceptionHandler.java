package com.example.akadion.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Validare Bean (@Valid) eșuată — 400 Bad Request cu erori per câmp. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        return Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "eroare", "Date invalide",
                "campuri", fieldErrors
        );
    }

    /** User negăsit după ID — 404 Not Found. */
    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleUserNotFound(UserNotFoundException ex) {
        return Map.of("status", HttpStatus.NOT_FOUND.value(), "eroare", ex.getMessage());
    }

    /** Operație admin aplicată unui user cu stare incompatibilă — 400 Bad Request. */
    @ExceptionHandler(InvalidUserStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleInvalidState(InvalidUserStateException ex) {
        return Map.of("status", HttpStatus.BAD_REQUEST.value(), "eroare", ex.getMessage());
    }

    /**
     * Emailul există deja direct în Keycloak (ocolind aplicația) — 409 Conflict.
     * Mesajul explică explicit că e nevoie de intervenție manuală în consola Keycloak.
     */
    @ExceptionHandler(KeycloakConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleKeycloakConflict(KeycloakConflictException ex) {
        log.error("Conflict Keycloak: {}", ex.getMessage());
        return Map.of("status", HttpStatus.CONFLICT.value(), "eroare", ex.getMessage());
    }

    /** Eroare de comunicare cu Keycloak Admin API — 502 Bad Gateway. */
    @ExceptionHandler(KeycloakIntegrationException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, Object> handleKeycloakIntegration(KeycloakIntegrationException ex) {
        log.error("Eroare integrare Keycloak: {}", ex.getMessage(), ex);
        return Map.of(
                "status", HttpStatus.BAD_GATEWAY.value(),
                "eroare", "Eroare de comunicare cu Keycloak. Verificați logurile și reîncercați.",
                "detalii", ex.getMessage()
        );
    }
}
