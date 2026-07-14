package com.example.akadion.exception;

/**
 * Aruncată când un User nu este găsit după ID.
 * Mapată la HTTP 404 de GlobalExceptionHandler.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long id) {
        super("Utilizatorul cu id=" + id + " nu a fost găsit.");
    }
}
