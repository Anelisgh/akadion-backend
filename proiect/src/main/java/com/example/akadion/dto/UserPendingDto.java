package com.example.akadion.dto;

/**
 * DTO pentru afișarea unei cereri PENDING în panoul de admin.
 * Include numărul de respingeri anterioare pe același mail — context util
 * pentru admin, fără a bloca automat cererea.
 */
public record UserPendingDto(
        Long id,
        String nume,
        String prenume,
        String mail,
        String facultate,
        String rolDorit,
        long nrRespingeriAnterioare,
        String stare
) {}
