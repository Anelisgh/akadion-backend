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

// Clasa aceasta este ca un "spital central" pentru erori.
// În mod normal, când codul din backend dă o eroare, serverul ar crăpa și ar trimite în browser o pagină urâtă de eroare (cu sute de linii de cod).
// Folosim @RestControllerAdvice ca să interceptăm orice eroare produsă în controllere și să o transformăm
// într-un răspuns JSON frumos și curat pe care frontend-ul (React) să îl poată citi și afișa utilizatorului.
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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
                "status", HttpStatus.BAD_REQUEST.value(),
                "eroare", "Date invalide",
                "campuri", fieldErrors
        );
    }

    // 2. Cazul în care un utilizator nu a fost găsit în baza de date.
    // Interceptează eroarea "UserNotFoundException" și returnează un JSON cu status 404 (Not Found).
    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND) // Cod HTTP 404
    public Map<String, Object> handleUserNotFound(UserNotFoundException ex) {
        return Map.of("status", HttpStatus.NOT_FOUND.value(), "eroare", ex.getMessage());
    }

    @ExceptionHandler(ResursaNegasitaException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleResursaNegasita(ResursaNegasitaException ex) {
        return Map.of("status", HttpStatus.NOT_FOUND.value(), "eroare", ex.getMessage());
    }

    // 3. Cazul în care se încearcă o acțiune nepermisă pe starea contului (ex: dezactivarea unui user deja inactiv).
    // Interceptează "InvalidUserStateException" și trimite înapoi codul HTTP 400 (Bad Request).
    @ExceptionHandler(InvalidUserStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST) // Cod HTTP 400
    public Map<String, Object> handleInvalidState(InvalidUserStateException ex) {
        return Map.of("status", HttpStatus.BAD_REQUEST.value(), "eroare", ex.getMessage());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleForbiddenOperation(ForbiddenOperationException ex) {
        return Map.of("status", HttpStatus.FORBIDDEN.value(), "eroare", ex.getMessage());
    }

    // 4. Cazul în care există un conflict în Keycloak (email-ul există deja).
    // Interceptează "KeycloakConflictException" și trimite înapoi codul HTTP 409 (Conflict).

    @ExceptionHandler(KeycloakConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT) // Cod HTTP 409
    public Map<String, Object> handleKeycloakConflict(KeycloakConflictException ex) {
        log.error("Conflict Keycloak: {}", ex.getMessage());
        return Map.of("status", HttpStatus.CONFLICT.value(), "eroare", ex.getMessage());
    }

    // 5. Cazul în care conexiunea/comunicarea cu Keycloak a eșuat la nivel de rețea sau server.
    // Interceptează "KeycloakIntegrationException" și trimite înapoi codul HTTP 502 (Bad Gateway).
    @ExceptionHandler(KeycloakIntegrationException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY) // Cod HTTP 502
    public Map<String, Object> handleKeycloakIntegration(KeycloakIntegrationException ex) {
        // Înregistrăm eroarea în consolă (loguri) împreună cu toată cauza ei (stack trace) pentru a o putea repara.
        log.error("Eroare integrare Keycloak: {}", ex.getMessage(), ex);
        return Map.of(
                "status", HttpStatus.BAD_GATEWAY.value(),
                "eroare", ex.getMessage() != null ? ex.getMessage() : "Eroare de comunicare cu Keycloak.",
                "detalii", ex.getMessage() != null ? ex.getMessage() : ""
        );
    }

    // 6. Acces interzis la resursa altui profesor (ownership check eșuat).
    @ExceptionHandler(AccesInterzisException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN) // Cod HTTP 403
    public Map<String, Object> handleAccesInterzis(AccesInterzisException ex) {
        return Map.of("status", HttpStatus.FORBIDDEN.value(), "eroare", ex.getMessage());
    }

    // 7. Date sau stări invalide trimise de client (ex: ID inexistent, curs inactiv, deja înrolat).
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST) // Cod HTTP 400
    public Map<String, Object> handleIllegalArgument(IllegalArgumentException ex) {
        return Map.of("status", HttpStatus.BAD_REQUEST.value(), "eroare", ex.getMessage());
    }

    // 8. Conflict de concurență la adăugarea simultană a aceleiași săptămâni.
    @ExceptionHandler(SaptamanaConcurentaException.class)
    @ResponseStatus(HttpStatus.CONFLICT) // Cod HTTP 409
    public Map<String, Object> handleSaptamanaConcurenta(SaptamanaConcurentaException ex) {
        return Map.of("status", HttpStatus.CONFLICT.value(), "eroare", ex.getMessage());
    }

    // 9. Eroare de stocare fișiere / comunicare cu MinIO.
    @ExceptionHandler(MinioIntegrationException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY) // Cod HTTP 502
    public Map<String, Object> handleMinioIntegration(MinioIntegrationException ex) {
        log.error("Eroare integrare MinIO: {}", ex.getMessage(), ex);
        return Map.of(
                "status", HttpStatus.BAD_GATEWAY.value(),
                "eroare", "Eroare de stocare fișiere. Verificați logurile și reîncercați."
        );
    }

    // 10. Fișierul încărcat depășește limita de dimensiune permisă (50MB).
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE) // Cod HTTP 413
    public Map<String, Object> handleFileTooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        return Map.of(
                "status", HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "eroare", "Fișierul depășește dimensiunea maximă permisă (50MB)."
        );
    }

    // 11. Depășire limită de apeluri per minut (Rate Limiting).
    @ExceptionHandler(TooManyRequestsException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS) // Cod HTTP 429
    public Map<String, Object> handleTooManyRequests(TooManyRequestsException ex) {
        return Map.of("status", HttpStatus.TOO_MANY_REQUESTS.value(), "eroare", ex.getMessage());
    }

    // 12. Eroare de comunicare cu serviciul RAG Chat (Timeout sau service offline).
    @ExceptionHandler(RagChatException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY) // Cod HTTP 502
    public Map<String, Object> handleRagChatException(RagChatException ex) {
        log.error("Eroare RAG Chat: {}", ex.getMessage(), ex);
        return Map.of("status", HttpStatus.BAD_GATEWAY.value(), "eroare", ex.getMessage());
    }
}
