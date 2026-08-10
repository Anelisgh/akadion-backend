package com.example.akadion.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record AuditLogDto(
        Long id,
        String numeTabel,
        Long idInregistrare,
        String operatie,
        String numeUtilizator,
        String emailUtilizator,
        Map<String, Object> valoriVechi,
        Map<String, Object> valoriNoi,
        OffsetDateTime creatLa
) {
}
