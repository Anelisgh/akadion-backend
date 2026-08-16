package com.example.akadion.auth.service;

import com.example.akadion.admin.entity.NumeTabelAudit;
import com.example.akadion.admin.entity.OperatieAudit;
import com.example.akadion.admin.service.AuditLogService;
import com.example.akadion.auth.dto.UpdateEmailRequestDto;
import com.example.akadion.auth.dto.UpdateProfileRequestDto;
import com.example.akadion.auth.dto.UserMeDto;
import com.example.akadion.common.entity.NumeStareCont;
import com.example.akadion.common.entity.User;
import com.example.akadion.common.entity.StareCont;
import com.example.akadion.exception.EmailDuplicatException;
import com.example.akadion.exception.ResursaNegasitaException;
import com.example.akadion.common.repository.StareContRepository;
import com.example.akadion.common.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final StareContRepository stareContRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final AuditLogService auditLogService;

    // Field injection intenționată: un service nu se poate auto-injecta prin constructor
    // (dependință circulară) fără @Lazy, iar Lombok @RequiredArgsConstructor nu suportă @Lazy per-parametru.
    // Necesar pentru ca apelurile interne prin self.xxx() să treacă prin proxy-ul @Transactional.
    @SuppressWarnings("java:S6813")
    @Autowired
    @Lazy
    private UserProfileService self;

    @Value("${app.frontend.base-url}")
    private String frontendUrl;

    private static final String KEYCLOAK_CLIENT_ID = "backend-login";
    private static final String INCOMPLETE_STATE = NumeStareCont.INCOMPLET.name();

    @Transactional
    public void inregistreazaUserNou(String idKeycloak, String email) {
        userRepository.findByMail(email)
                .ifPresent(existingUser -> {
                    throw new EmailDuplicatException(
                            "Există deja un cont local asociat cu emailul " + existingUser.getMail() + ".");
                });

        StareCont incomplete = stareContRepository.findByDenumire(INCOMPLETE_STATE)
                .orElseThrow(() -> new IllegalStateException("Starea INCOMPLET lipsește din DB."));

        User user = new User();
        user.setIdKeycloak(idKeycloak);
        user.setMail(email);
        user.setStareCont(incomplete);
        user.setNrRespingeri(0);

        user = userRepository.save(user);

        auditLogService.inregistreaza(
                NumeTabelAudit.APP_USER,
                user.getId(),
                OperatieAudit.CREARE_CONT,
                null,
                Map.of("mail", email, "stare", INCOMPLETE_STATE)
        );
        log.info("Primul login pentru sub={}. Utilizator local creat în starea INCOMPLET.", idKeycloak);
    }

    @Transactional
    public UserMeDto updateProfile(String idKeycloak, UpdateProfileRequestDto dto) {
        User user = getUserByIdKeycloak(idKeycloak);

        String oldNume = user.getNume();
        String oldPrenume = user.getPrenume();
        String oldFacultate = user.getFacultate();

        user.setNume(dto.nume().trim());
        user.setPrenume(dto.prenume().trim());
        user.setFacultate(dto.facultate().trim());

        User savedUser = userRepository.save(user);

        auditLogService.inregistreaza(
                NumeTabelAudit.APP_USER,
                savedUser.getId(),
                OperatieAudit.EDITARE_PROFIL,
                Map.of("nume", oldNume == null ? "" : oldNume,
                       "prenume", oldPrenume == null ? "" : oldPrenume,
                       "facultate", oldFacultate == null ? "" : oldFacultate),
                Map.of("nume", savedUser.getNume(),
                       "prenume", savedUser.getPrenume(),
                       "facultate", savedUser.getFacultate())
        );
        log.info("Profil actualizat (local) pentru user-ul cu idKeycloak={}", idKeycloak);

        return toUserMeDto(savedUser);
    }

    public UserMeDto updateEmail(String idKeycloak, UpdateEmailRequestDto dto) {
        User user = getUserByIdKeycloak(idKeycloak);

        String newEmail = dto.newEmail().trim().toLowerCase(Locale.ROOT);
        String oldEmail = user.getMail();

        // 0. Idempotență (Early Return)
        if (newEmail.equals(oldEmail)) {
            log.info("Email-ul este deja {}, nu se face nicio modificare pentru idKeycloak={}", newEmail, idKeycloak);
            return toUserMeDto(user);
        }

        // 1. Verificare Prealabilă (Pre-Check)
        if (userRepository.findByMail(newEmail).isPresent()) {
            throw new EmailDuplicatException("Acest email este deja utilizat de alt cont.");
        }

        // 2. Keycloak UPDATE (apel de rețea — ținut deliberat în afara oricărei tranzacții DB)
        keycloakAdminService.updateEmail(idKeycloak, newEmail, true);

        // 3. Local DB UPDATE + audit, într-o tranzacție proprie scurtă (self-proxy)
        User savedUser;
        try {
            savedUser = self.salveazaEmailSiAuditeaza(user.getId(), oldEmail, newEmail);
        } catch (DataIntegrityViolationException e) {
            log.error("Conflict la salvarea email-ului în baza de date (race condition) pentru idKeycloak={}. Se efectuează rollback în Keycloak...", idKeycloak);

            try {
                keycloakAdminService.updateEmail(idKeycloak, oldEmail, true);
            } catch (Exception rollbackException) {
                log.warn("Eroare la rollback email Keycloak pentru sub={}: {}", idKeycloak, rollbackException.getMessage());
            }

            throw new EmailDuplicatException("Acest email este deja utilizat de alt cont (conflict simultan).");
        }

        // 5. Declanșare VERIFY_EMAIL (apel de rețea — după ce DB e deja confirmat)
        try {
            keycloakAdminService.executeActionsEmail(
                    idKeycloak,
                    List.of("VERIFY_EMAIL"),
                    KEYCLOAK_CLIENT_ID,
                    frontendUrl
            );
        } catch (Exception e) {
            log.warn("Nu s-a putut trimite e-mailul VERIFY_EMAIL din Keycloak (posibil SMTP neconfigurat) pentru idKeycloak={}: {}", idKeycloak, e.getMessage());
        }

        log.info("Email actualizat cu succes pentru idKeycloak={}.", idKeycloak);
        return toUserMeDto(savedUser);
    }

    @Transactional
    public User salveazaEmailSiAuditeaza(Long userId, String oldEmail, String newEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResursaNegasitaException("Utilizatorul cu id=" + userId + " nu a fost gasit."));
        user.setMail(newEmail);
        User savedUser = userRepository.saveAndFlush(user);

        auditLogService.inregistreaza(
                NumeTabelAudit.APP_USER,
                savedUser.getId(),
                OperatieAudit.SCHIMBARE_EMAIL,
                Map.of("mail", oldEmail),
                Map.of("mail", newEmail)
        );

        return savedUser;
    }

    public void requestPasswordReset(String idKeycloak) {
        // Asigurăm-ne că userul există local (deși token-ul lui ne garantează asta)
        getUserByIdKeycloak(idKeycloak);

        keycloakAdminService.executeActionsEmail(
                idKeycloak,
                List.of("UPDATE_PASSWORD"),
                KEYCLOAK_CLIENT_ID,
                frontendUrl
        );
        log.info("Link de resetare parolă trimis pentru idKeycloak={}", idKeycloak);
    }

    private User getUserByIdKeycloak(String idKeycloak) {
        return userRepository.findByIdKeycloak(idKeycloak)
                .orElseThrow(() -> new ResursaNegasitaException("Nu s-a găsit utilizatorul cu idKeycloak=" + idKeycloak));
    }

    private UserMeDto toUserMeDto(User user) {
        return new UserMeDto(
                user.getId(),
                user.getNume(),
                user.getPrenume(),
                user.getMail(),
                user.getRolDenumire(),
                user.getFacultate(),
                user.getStareCont().getDenumire()
        );
    }
}
