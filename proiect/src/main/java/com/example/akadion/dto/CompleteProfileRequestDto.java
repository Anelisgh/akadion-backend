package com.example.akadion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DTO pentru finalizarea/completarea profilului utilizatorului după înregistrarea nativă din Keycloak.
 */
public record CompleteProfileRequestDto(

        @NotBlank(message = "Numele nu poate fi gol")
        String nume,

        @NotBlank(message = "Prenumele nu poate fi gol")
        String prenume,

        // Facultatea este nullable/opțională
        String facultate,

        @NotBlank(message = "Rolul dorit este obligatoriu")
        @Pattern(
                regexp = "^(PROFESOR|STUDENT)$",
                message = "Rolul dorit poate fi doar PROFESOR sau STUDENT"
        )
        String rolDorit
) {}
