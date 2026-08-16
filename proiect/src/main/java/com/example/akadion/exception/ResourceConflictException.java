package com.example.akadion.exception;

public abstract class ResourceConflictException extends RuntimeException {
    protected ResourceConflictException(String message) {
        super(message);
    }
}
