package com.example.akadion.dto;

public record ProfesorDetaliiResponseDto(
        Long id,
        String nume,
        String prenume,
        String mail,
        String facultate
) {}
