package com.example.akadion.dto;

import java.time.OffsetDateTime;

public record ConversatieDTO(
    Long id,
    Long cursId,
    String titlu,
    OffsetDateTime createdAt
) {}
