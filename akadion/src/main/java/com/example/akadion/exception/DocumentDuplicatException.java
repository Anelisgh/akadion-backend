package com.example.akadion.exception;

public class DocumentDuplicatException extends ResourceConflictException {
    public DocumentDuplicatException(String message) {
        super(message);
    }
}
