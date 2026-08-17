package com.example.akadion.curs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// Payload-ul trimis de embedder_service (Python) la finalul job-ului async de
// procesare a imaginilor. Contract neconfirmat oficial - vezi VISION_HANDOFF_SPRINGBOOT.md.
public record ImageStatusCallbackDto(
        @JsonProperty("document_id")
        @NotNull(message = "document_id este obligatoriu.")
        Long documentId,

        @NotBlank(message = "status este obligatoriu.")
        String status,

        @JsonProperty("images_indexed")
        @NotNull(message = "images_indexed este obligatoriu.")
        @PositiveOrZero(message = "images_indexed nu poate fi negativ.")
        Integer imagesIndexed,

        @JsonProperty("images_failed")
        @NotNull(message = "images_failed este obligatoriu.")
        @PositiveOrZero(message = "images_failed nu poate fi negativ.")
        Integer imagesFailed
) {}
