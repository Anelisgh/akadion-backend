package com.example.akadion.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequestDto(
        @NotBlank(message = "Numele este obligatoriu.")
        String nume,

        @NotBlank(message = "Prenumele este obligatoriu.")
        String prenume,

        @NotBlank(message = "Facultatea este obligatorie.")
        String facultate
) {
}
