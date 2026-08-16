package com.example.akadion.akychat.dto;

import java.time.OffsetDateTime;

public record ConversatieDto(
    Long id,
    Long cursId,
    String titlu,
    OffsetDateTime createdAt
) {}
