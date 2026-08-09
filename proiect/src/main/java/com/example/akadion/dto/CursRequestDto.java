package com.example.akadion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CursRequestDto(
        @NotBlank(message = "Denumirea cursului este obligatorie.")
        @Size(max = 150, message = "Denumirea nu poate depăși 150 de caractere.")
        String denumire,

        @Size(max = 1000, message = "Descrierea nu poate depăși 1000 de caractere.")
        String descriere,

        LocalDate dataInceput
) {}
