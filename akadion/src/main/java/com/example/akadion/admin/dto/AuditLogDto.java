package com.example.akadion.admin.dto;

import com.example.akadion.admin.entity.NumeTabelAudit;
import com.example.akadion.admin.entity.OperatieAudit;

import java.time.OffsetDateTime;
import java.util.Map;

public record AuditLogDto(
        Long id,
        NumeTabelAudit numeTabel,
        Long idInregistrare,
        OperatieAudit operatie,
        String numeUtilizator,
        String emailUtilizator,
        Map<String, Object> valoriVechi,
        Map<String, Object> valoriNoi,
        OffsetDateTime createdAt
) {
}
