package com.example.akadion.dto;

public record QuizGenerateRequestDto(
    Long documentId,
    Integer nrIntrebari,
    String dificultate
) {}
