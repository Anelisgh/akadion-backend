package com.example.akadion.exception;

public class RagChatException extends RuntimeException {
    public RagChatException(String message) {
        super(message);
    }

    public RagChatException(String message, Throwable cause) {
        super(message, cause);
    }
}
