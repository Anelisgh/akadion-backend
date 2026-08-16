package com.example.akadion.quiz.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record QuizGenerateRequestDto(
    Long documentId,
    @Min(1) @Max(20) Integer nrIntrebari,
    @Size(max = 50) String dificultate
) {}
