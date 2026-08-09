package com.example.akadion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NouaIntrebareRequest(
    @NotBlank(message = "Întrebarea nu poate fi goală.")
    @Size(max = 1000, message = "Întrebarea nu poate depăși 1000 de caractere.")
    String intrebare
) {}
