package com.example.akadion.curs.dto;

public record ProfesorDetaliiResponseDto(
        Long id,
        String nume,
        String prenume,
        String mail,
        String facultate
) {}
