package com.example.akadion.curs.dto;

public record SaptamanaStudentResponseDto(
        Long id,
        int nrSaptamana,
        String descriere,
        boolean finalizata
) {}
