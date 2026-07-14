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

    // 3. Cazul în care se încearcă o acțiune nepermisă pe starea contului (ex: dezactivarea unui user deja inactiv).
    // Interceptează "InvalidUserStateException" și trimite înapoi codul HTTP 400 (Bad Request).
    @ExceptionHandler(InvalidUserStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST) // Cod HTTP 400
    public Map<String, Object> handleInvalidState(InvalidUserStateException ex) {
        return Map.of("status", HttpStatus.BAD_REQUEST.value(), "eroare", ex.getMessage());
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
                "eroare", "Eroare de comunicare cu Keycloak. Verificați logurile și reîncercați.",
                "detalii", ex.getMessage()
        );
    }
}
