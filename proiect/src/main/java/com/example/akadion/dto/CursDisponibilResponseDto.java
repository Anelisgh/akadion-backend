package com.example.akadion.dto;

import java.time.LocalDate;

public record CursDisponibilResponseDto(
        Long id,
        String denumire,
        String descriere,
        String profesorNume,
        String profesorPrenume,
        LocalDate dataInceput,
        LocalDate dataSfarsit,
        int nrSaptamani
) {}
