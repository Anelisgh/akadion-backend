package com.example.akadion.dto;

import jakarta.validation.constraints.Size;

public record SaptamanaRequestDto(
        @Size(max = 500, message = "Descrierea săptămânii nu poate depăși 500 de caractere.")
        String descriere
) {}
