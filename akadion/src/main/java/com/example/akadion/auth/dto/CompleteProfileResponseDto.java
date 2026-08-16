package com.example.akadion.auth.dto;

import java.time.OffsetDateTime;

public record CompleteProfileResponseDto(
        Long id,
        String nume,
        String prenume,
        String mail,
        String facultate,
        String rolDorit,
        String stare,
        OffsetDateTime createdAt,
        String message
) {}
