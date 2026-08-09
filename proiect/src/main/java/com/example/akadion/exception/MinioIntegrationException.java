package com.example.akadion.exception;

public class MinioIntegrationException extends RuntimeException {
    
    public MinioIntegrationException(String message) {
        super(message);
    }
    
    public MinioIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
