package com.example.akadion.dto;

public record SaptamanaStudentResponseDto(
        Long id,
        int nrSaptamana,
        String descriere,
        boolean finalizata
) {}
