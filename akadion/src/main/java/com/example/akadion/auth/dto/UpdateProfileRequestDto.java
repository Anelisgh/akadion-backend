package com.example.akadion.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequestDto(
        @NotBlank(message = "Numele este obligatoriu.")
        @Size(max = 100, message = "Numele poate avea cel mult 100 de caractere.")
        String nume,

        @NotBlank(message = "Prenumele este obligatoriu.")
        @Size(max = 100, message = "Prenumele poate avea cel mult 100 de caractere.")
        String prenume,

        @NotBlank(message = "Facultatea este obligatorie.")
        @Size(max = 100, message = "Facultatea poate avea cel mult 100 de caractere.")
        String facultate
) {
}
