package com.example.akadion.quiz.dto;

import java.util.List;

public record QuizGenerateResponseDto(
    Long incercareId,
    List<QuizQuestionProjectionDto> intrebari
) {}
