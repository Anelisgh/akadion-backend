package com.example.akadion.dto;

public record DocumentResponseDto(
        Long id,
        String titlu,
        String statusIndex,
        boolean activ,
        String urlVizualizare,
        String urlDescarcare
) {}
