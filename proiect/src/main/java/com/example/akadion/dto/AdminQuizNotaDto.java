package com.example.akadion.dto;

import java.time.OffsetDateTime;

public record AdminQuizNotaDto(
    Long incercareId,
    Long studentId,
    String studentNume,
    String studentPrenume,
    String studentEmail,
    Integer scor,
    Integer nrIntrebari,
    Double procentaj,
    OffsetDateTime dataFinalizare
) {}
