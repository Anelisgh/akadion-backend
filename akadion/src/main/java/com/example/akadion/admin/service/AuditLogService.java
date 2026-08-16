package com.example.akadion.admin.service;

import com.example.akadion.admin.dto.AuditLogDto;
import com.example.akadion.admin.entity.AuditLog;
import com.example.akadion.admin.entity.NumeTabelAudit;
import com.example.akadion.admin.entity.OperatieAudit;
import com.example.akadion.admin.repository.AuditLogRepository;
import com.example.akadion.common.entity.User;
import com.example.akadion.common.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final String SYSTEM_ACTOR = "system";

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final AuditorAware<String> auditorProvider;

    @Transactional(readOnly = true)
    public Slice<AuditLogDto> getAuditLog(Pageable pageable) {
        Slice<AuditLog> slice = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);

        List<String> keycloakIds = slice.getContent().stream()
                .map(AuditLog::getUtilizator)
                .filter(u -> u != null && !u.equals(SYSTEM_ACTOR))
                .distinct()
                .toList();

        Map<String, User> userMap = Collections.emptyMap();
        if (!keycloakIds.isEmpty()) {
            userMap = userRepository.findByIdKeycloakIn(keycloakIds).stream()
                    .collect(Collectors.toMap(User::getIdKeycloak, user -> user));
        }

        final Map<String, User> finalUserMap = userMap;

        return slice.map(logEntry -> {
            User dbUser = finalUserMap.get(logEntry.getUtilizator());
            String numeComplet = "Utilizator Necunoscut";
            String emailUtilizator = null;

            if (SYSTEM_ACTOR.equals(logEntry.getUtilizator())) {
                numeComplet = "Sistem";
                emailUtilizator = "system@akadion";
            } else if (dbUser != null) {
                numeComplet = (dbUser.getNume() != null ? dbUser.getNume() : "") + " "
                        + (dbUser.getPrenume() != null ? dbUser.getPrenume() : "");
                emailUtilizator = dbUser.getMail();
            }

            return new AuditLogDto(
                    logEntry.getId(),
                    logEntry.getNumeTabel(),
                    logEntry.getIdInregistrare(),
                    logEntry.getOperatie(),
                    numeComplet.trim(),
                    emailUtilizator,
                    logEntry.getValoriVechi(),
                    logEntry.getValoriNoi(),
                    logEntry.getCreatedAt()
            );
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void inregistreaza(NumeTabelAudit numeTabel, Long idInregistrare, OperatieAudit operatie,
                              Map<String, Object> valoriVechi, Map<String, Object> valoriNoi) {
        String utilizator = auditorProvider.getCurrentAuditor().orElse(SYSTEM_ACTOR);

        AuditLog auditLog = AuditLog.builder()
                .numeTabel(numeTabel)
                .idInregistrare(idInregistrare)
                .operatie(operatie)
                .utilizator(utilizator)
                .valoriVechi(valoriVechi)
                .valoriNoi(valoriNoi)
                .build();

        auditLogRepository.save(auditLog);
        log.info("Audit log înregistrat: tabel={}, id={}, operatie={}, utilizator={}",
                numeTabel, idInregistrare, operatie, utilizator);
    }
}
