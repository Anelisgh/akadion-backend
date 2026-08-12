package com.example.akadion.dto;

import com.example.akadion.entity.StatusIncercareQuiz;

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
    StatusIncercareQuiz status,
    List<QuizQuestionFeedbackDto> detalii,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
