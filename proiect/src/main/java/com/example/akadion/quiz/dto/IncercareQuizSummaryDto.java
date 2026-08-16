package com.example.akadion.quiz.dto;

import java.time.OffsetDateTime;

public record IncercareQuizSummaryDto(
    Long incercareId,
    Long cursId,
    String cursDenumire,
    Long documentId,
    String documentTitlu,
    Integer scor,
    Integer nrIntrebari,
    Double procentaj,
    OffsetDateTime createdAt
) {}
