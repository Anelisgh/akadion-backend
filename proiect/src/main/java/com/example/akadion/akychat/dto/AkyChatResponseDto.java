package com.example.akadion.akychat.dto;

import com.example.akadion.common.dto.AkySursaDocumentDto;

import java.util.List;

public record AkyChatResponseDto(
    String raspuns,
    List<AkySursaDocumentDto> surseFolosite
) {}
