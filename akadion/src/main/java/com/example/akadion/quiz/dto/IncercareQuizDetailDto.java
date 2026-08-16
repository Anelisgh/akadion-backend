package com.example.akadion.quiz.dto;

import com.example.akadion.quiz.entity.IncercareQuizStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record IncercareQuizDetailDto(
    Long incercareId,
    Long cursId,
    String cursDenumire,
    Long documentId,
    String documentTitlu,
    Integer scor,
    Integer nrIntrebari,
    Double procentaj,
    IncercareQuizStatus status,
    List<QuizQuestionFeedbackDto> detalii,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
