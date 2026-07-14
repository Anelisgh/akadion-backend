package com.example.akadion.dto;

/**
 * DTO returnat de GET /api/auth/me.
 * Include rolul și starea curentă a contului din DB (surse de adevăr).
 */
public record UserMeDto(
        Long id,
        String nume,
        String prenume,
        String mail,
        String rol,
        String stareCont
) {}
