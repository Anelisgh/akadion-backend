package com.example.akadion.quiz.dto;

import java.util.List;

public record QuizFinalizatResponseDto(
    Long incercareId,
    Integer scor,
    Integer nrIntrebari,
    Double procentaj,
    List<QuizQuestionFeedbackDto> detalii
) {}
