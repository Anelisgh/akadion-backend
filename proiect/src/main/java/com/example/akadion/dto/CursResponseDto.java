package com.example.akadion.dto;

import java.time.LocalDate;

public record CursResponseDto(
        Long id,
        String denumire,
        String descriere,
        LocalDate dataInceput,
        LocalDate dataSfarsit,
        boolean activ,
        int nrSaptamaniCurente,
        String profesorNume,
        String profesorPrenume,
        int nrStudentiInscrisi
) {}
