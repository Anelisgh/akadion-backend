package com.example.akadion.service;

import com.example.akadion.dto.AuditLogDto;
import com.example.akadion.entity.AuditLog;
import com.example.akadion.entity.User;
import com.example.akadion.repository.AuditLogRepository;
import com.example.akadion.repository.UserRepository;
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

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final AuditorAware<String> auditorProvider;

    @Transactional(readOnly = true)
    public Slice<AuditLogDto> getAuditLog(Pageable pageable) {
        Slice<AuditLog> slice = auditLogRepository.findAllByOrderByCreatLaDesc(pageable);

        List<String> keycloakIds = slice.getContent().stream()
                .map(AuditLog::getUtilizator)
                .filter(u -> u != null && !u.equals("system"))
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

            if ("system".equals(logEntry.getUtilizator())) {
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
                    logEntry.getCreatLa()
            );
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void inregistreaza(String numeTabel, Long idInregistrare, String operatie,
                              Map<String, Object> valoriVechi, Map<String, Object> valoriNoi) {
        String utilizator = auditorProvider.getCurrentAuditor().orElse("system");

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
