package com.example.akadion.dto;

public record DocumentStudentResponseDto(
        Long id,
        String titlu,
        String urlVizualizare,
        String urlDescarcare
) {}
