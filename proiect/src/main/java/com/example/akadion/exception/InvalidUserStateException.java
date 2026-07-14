package com.example.akadion.exception;

/**
 * Aruncată când o operație admin (accept/respinge/dezactivare) e aplicată
 * unui utilizator cu starea contului incompatibilă.
 * Mapată la HTTP 400 de GlobalExceptionHandler.
 */
public class InvalidUserStateException extends RuntimeException {

    public InvalidUserStateException(String message) {
        super(message);
    }
}
