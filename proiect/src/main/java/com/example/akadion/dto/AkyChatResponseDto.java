package com.example.akadion.dto;

import java.util.List;

public record AkyChatResponseDto(
    String raspuns,
    List<AkySursaDocumentDto> surseFolosite
) {}
