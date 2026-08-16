package com.example.akadion.curs.dto;

public record DocumentStudentResponseDto(
        Long id,
        String titlu,
        String urlVizualizare,
        String urlDescarcare
) {}
