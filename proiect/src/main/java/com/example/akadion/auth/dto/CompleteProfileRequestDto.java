package com.example.akadion.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO pentru finalizarea/completarea profilului utilizatorului după înregistrarea nativă din Keycloak.
 */
public record CompleteProfileRequestDto(

        @NotBlank(message = "Numele nu poate fi gol")
        @Size(max = 100, message = "Numele poate avea cel mult 100 de caractere.")
        String nume,

        @NotBlank(message = "Prenumele nu poate fi gol")
        @Size(max = 100, message = "Prenumele poate avea cel mult 100 de caractere.")
        String prenume,

        // Facultatea este nullable/opțională
        @Size(max = 100, message = "Facultatea poate avea cel mult 100 de caractere.")
        String facultate,

        @NotBlank(message = "Rolul dorit este obligatoriu")
        @Pattern(
                regexp = "^(PROFESOR|STUDENT)$",
                message = "Rolul dorit poate fi doar PROFESOR sau STUDENT"
        )
        String rolDorit
) {}
