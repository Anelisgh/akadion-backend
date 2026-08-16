package com.example.akadion.curs.dto;

import java.time.LocalDate;

public record CursInrolatResponseDto(
        Long id,
        String denumire,
        String descriere,
        LocalDate dataInceput,
        LocalDate dataSfarsit,
        String profesorNume,
        String profesorPrenume,
        double procentajProgres,
        int nrSaptamani
) {}
