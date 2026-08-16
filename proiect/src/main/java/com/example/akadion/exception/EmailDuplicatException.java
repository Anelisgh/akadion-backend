package com.example.akadion.exception;

public class EmailDuplicatException extends ResourceConflictException {
    public EmailDuplicatException(String message) {
        super(message);
    }
}
