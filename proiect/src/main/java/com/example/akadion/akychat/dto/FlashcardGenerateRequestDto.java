package com.example.akadion.akychat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record FlashcardGenerateRequestDto(
    Long documentId,
    @Min(1) @Max(20) Integer nrFlashcards
) {}
