package com.example.akadion.dto;

import java.util.List;

public record QuizGenerateResponseDto(
    Long incercareId,
    List<QuizQuestionProjectionDto> intrebari
) {}
