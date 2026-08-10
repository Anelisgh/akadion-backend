package com.example.akadion.service;

import com.example.akadion.entity.AuditLog;
import com.example.akadion.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final com.example.akadion.repository.UserRepository userRepository;
    private final AuditorAware<String> auditorProvider;

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Slice<com.example.akadion.dto.AuditLogDto> getAuditLog(org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Slice<AuditLog> slice = auditLogRepository.findAllByOrderByCreatLaDesc(pageable);
        
        java.util.List<String> keycloakIds = slice.getContent().stream()
                .map(AuditLog::getUtilizator)
                .filter(u -> u != null && !u.equals("system"))
                .distinct()
                .toList();

        java.util.Map<String, com.example.akadion.entity.User> userMap = java.util.Collections.emptyMap();
        if (!keycloakIds.isEmpty()) {
            userMap = userRepository.findByIdKeycloakIn(keycloakIds).stream()
                    .collect(java.util.stream.Collectors.toMap(
                            com.example.akadion.entity.User::getIdKeycloak,
                            u -> u
                    ));
        }
        
        final java.util.Map<String, com.example.akadion.entity.User> finalUserMap = userMap;

        return slice.map(log -> {
            com.example.akadion.entity.User dbUser = finalUserMap.get(log.getUtilizator());
            String numeComplet = "Utilizator Necunoscut";
            String emailUtilizator = null;
            
            if ("system".equals(log.getUtilizator())) {
                numeComplet = "Sistem";
                emailUtilizator = "system@akadion";
            } else if (dbUser != null) {
                numeComplet = (dbUser.getNume() != null ? dbUser.getNume() : "") + " " + (dbUser.getPrenume() != null ? dbUser.getPrenume() : "");
                emailUtilizator = dbUser.getMail();
            }
            
            return new com.example.akadion.dto.AuditLogDto(
                    log.getId(),
                    log.getNumeTabel(),
                    log.getIdInregistrare(),
                    log.getOperatie(),
                    numeComplet.trim(),
                    emailUtilizator,
                    log.getValoriVechi(),
                    log.getValoriNoi(),
                    log.getCreatLa()
            );
        });
    }

    /**
     * Înregistrează un eveniment în tabela audit_log.
     * Propagation.MANDATORY forțează apelarea acestei metode doar din interiorul
     * unei tranzacții deja existente (pt. a evita loguri orfane sau asincronicități ciudate).
     */
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
