package com.example.akadion.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.security.access.AccessDeniedException;

import java.util.LinkedHashMap;
import java.util.Map;

// Punct central de mapare excepție → răspuns HTTP. @RestControllerAdvice interceptează orice
// excepție aruncată din controllere și o transformă într-un JSON consistent {status, eroare}.
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String STATUS_KEY = "status";
    private static final String EROARE_KEY = "eroare";

    // Metodă utilitară privată care construiește un răspuns JSON uniform {status, eroare}.
    private Map<String, Object> buildError(HttpStatus status, String mesaj) {
        return Map.of(STATUS_KEY, status.value(), EROARE_KEY, mesaj);
    }

    // 1. Cazul în care datele trimise în formulare nu sunt valide (de exemplu, nume prea scurt sau email incorect).
    // @ExceptionHandler(MethodArgumentNotValidException.class) îi spune lui Spring să ruleze această metodă
    // ori de câte ori validarea adnotărilor @Valid a dat greș.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST) // Răspundem cu codul HTTP 400 (Bad Request)
    public Map<String, Object> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        // Parcurgem toate câmpurile care au dat eroare și salvăm numele câmpului + mesajul specific.
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        // Returnăm un obiect JSON simplu care conține codul de eroare și lista de câmpuri greșite.
        return Map.of(
                STATUS_KEY, HttpStatus.BAD_REQUEST.value(),
                EROARE_KEY, "Date invalide",
                "campuri", fieldErrors
        );
    }

    @ExceptionHandler(ResursaNegasitaException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleResursaNegasita(ResursaNegasitaException ex) {
        log.warn("Resursă negăsită: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Acțiune administrativă respinsă din cauza stării curente a contului (ex: aprobare user care nu e PENDING).
    @ExceptionHandler(InvalidUserStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST) // Cod HTTP 400
    public Map<String, Object> handleInvalidState(InvalidUserStateException ex) {
        log.warn("Stare utilizator invalidă: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleForbiddenOperation(ForbiddenOperationException ex) {
        log.warn("Operațiune interzisă: {}", ex.getMessage());
        return buildError(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // Conexiunea/comunicarea cu Keycloak a eșuat la nivel de rețea sau server.
    // Interceptează "KeycloakIntegrationException" și trimite înapoi codul HTTP 502 (Bad Gateway).
    @ExceptionHandler(KeycloakIntegrationException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY) // Cod HTTP 502
    public Map<String, Object> handleKeycloakIntegration(KeycloakIntegrationException ex) {
        // Înregistrăm eroarea în consolă (loguri) împreună cu toată cauza ei (stack trace) pentru a o putea repara.
        log.error("Eroare integrare Keycloak: {}", ex.getMessage(), ex);
        return Map.of(
                STATUS_KEY, HttpStatus.BAD_GATEWAY.value(),
                EROARE_KEY, ex.getMessage() != null ? ex.getMessage() : "Eroare de comunicare cu Keycloak.",
                "detalii", ex.getMessage() != null ? ex.getMessage() : ""
        );
    }

    // Date sau stări invalide trimise de client (ex: ID inexistent, curs inactiv, deja înrolat).
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST) // Cod HTTP 400
    public Map<String, Object> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Argument ilegal: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Handler consolidat pentru toate excepțiile de tip conflict (409).
    // Acoperă: SaptamanaConcurentaException, DocumentDuplicatException, IncercareQuizFinalizataException, EmailDuplicatException.
    @ExceptionHandler(ResourceConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleResourceConflict(ResourceConflictException ex) {
        log.warn("Conflict resursă: {}", ex.getMessage());
        return buildError(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Eroare de stocare fișiere / comunicare cu MinIO.
    @ExceptionHandler(MinioIntegrationException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY) // Cod HTTP 502
    public Map<String, Object> handleMinioIntegration(MinioIntegrationException ex) {
        log.error("Eroare integrare MinIO: {}", ex.getMessage(), ex);
        return buildError(HttpStatus.BAD_GATEWAY, "Eroare de stocare fișiere. Verificați logurile și reîncercați.");
    }

    // Fișierul încărcat depășește limita de dimensiune permisă (50MB).
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.CONTENT_TOO_LARGE) // Cod HTTP 413
    public Map<String, Object> handleFileTooLarge(MaxUploadSizeExceededException ex) {
        return buildError(HttpStatus.CONTENT_TOO_LARGE, "Fișierul depășește dimensiunea maximă permisă (50MB).");
    }

    // Depășire limită de apeluri per minut (Rate Limiting).
    @ExceptionHandler(TooManyRequestsException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS) // Cod HTTP 429
    public Map<String, Object> handleTooManyRequests(TooManyRequestsException ex) {
        log.warn("Rate limit depășit: {}", ex.getMessage());
        return buildError(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    // Eroare de comunicare cu serviciul RAG Chat (Timeout sau service offline).
    @ExceptionHandler(RagChatException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY) // Cod HTTP 502
    public Map<String, Object> handleRagChatException(RagChatException ex) {
        log.error("Eroare RAG Chat: {}", ex.getMessage(), ex);
        return buildError(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    // Spring Security access denied (403) - delegare corectă în loc să fie ascunsă de handler-ul generic 500
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Acces interzis (Spring Security): {}", ex.getMessage());
        return buildError(HttpStatus.FORBIDDEN, "Acces interzis.");
    }

    // Metodă HTTP nepermisă (ex: POST în loc de PATCH)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Map<String, Object> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Metodă HTTP nepermisă: {}", ex.getMessage());
        return buildError(HttpStatus.METHOD_NOT_ALLOWED, "Metoda HTTP nu este permisă pentru acest endpoint.");
    }

    // Handler catch-all: garantează un contract JSON {status, eroare} consistent pentru orice excepție neprevăzută.
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleUnexpected(Exception ex) {
        log.error("Eroare neașteptată: {}", ex.getMessage(), ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "A apărut o eroare neașteptată. Vă rugăm să încercați din nou.");
    }
}
