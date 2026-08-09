package com.example.akadion.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResursaNegasitaException extends RuntimeException {
    public ResursaNegasitaException(String message) {
        super(message);
    }
}
