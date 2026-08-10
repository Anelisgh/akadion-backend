package com.example.akadion.dto;

public record FlashcardGenerateRequestDto(
    Long documentId,
    Integer nrFlashcards
) {}
