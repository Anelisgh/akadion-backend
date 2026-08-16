package com.example.akadion.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateEmailRequestDto(
        @NotBlank(message = "Emailul este obligatoriu.")
        @Email(message = "Format email invalid.")
        String newEmail
) {
}
