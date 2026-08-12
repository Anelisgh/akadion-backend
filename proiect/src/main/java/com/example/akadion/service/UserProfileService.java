package com.example.akadion.service;

import com.example.akadion.dto.UpdateEmailRequestDto;
import com.example.akadion.dto.UpdateProfileRequestDto;
import com.example.akadion.dto.UserMeDto;
import com.example.akadion.entity.User;
import com.example.akadion.exception.ForbiddenOperationException;
import com.example.akadion.exception.UserNotFoundException;
import com.example.akadion.repository.StareContRepository;
import com.example.akadion.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.frontend.base-url}")
    private String frontendUrl;

    private static final String KEYCLOAK_CLIENT_ID = "backend-login";

    @Transactional
    public void inregistreazaUserNou(String idKeycloak, String email) {
        userRepository.findByMail(email)
                .ifPresent(existingUser -> {
                    throw new ForbiddenOperationException(
                            "Există deja un cont local asociat cu emailul " + existingUser.getMail() + ".");
                });

        com.example.akadion.entity.StareCont incomplete = stareContRepository.findByDenumire("INCOMPLET")
                .orElseThrow(() -> new IllegalStateException("Starea INCOMPLET lipsește din DB."));

        User user = new User();
        user.setIdKeycloak(idKeycloak);
        user.setMail(email);
        user.setStareCont(incomplete);
        user.setNrRespingeri(0);

        user = userRepository.save(user);

        auditLogService.inregistreaza(
                "app_user",
                user.getId(),
                "CREARE_CONT",
                null,
                Map.of("mail", email, "stare", "INCOMPLET")
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
                "app_user",
                savedUser.getId(),
                "EDITARE_PROFIL",
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

    @Transactional
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
            throw new ForbiddenOperationException("Acest email este deja utilizat de alt cont.");
        }

        // 2. Keycloak UPDATE
        keycloakAdminService.updateEmail(idKeycloak, newEmail, true);

        // 3. Local DB UPDATE cu saveAndFlush pentru a prinde DataIntegrityViolationException imediat
        try {
            user.setMail(newEmail);
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            log.error("Conflict la salvarea email-ului în baza de date (race condition) pentru idKeycloak={}. Se efectuează rollback în Keycloak...", idKeycloak);
            
            try {
                keycloakAdminService.updateEmail(idKeycloak, oldEmail, true);
            } catch (Exception rollbackException) {
                log.warn("Eroare la rollback email Keycloak pentru sub={}: {}", idKeycloak, rollbackException.getMessage());
            }
            
            throw new ForbiddenOperationException("Acest email este deja utilizat de alt cont (conflict simultan).");
        }

        // 5. Declanșare VERIFY_EMAIL
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

        auditLogService.inregistreaza(
                "app_user",
                user.getId(),
                "SCHIMBARE_EMAIL",
                Map.of("mail", oldEmail),
                Map.of("mail", newEmail)
        );

        log.info("Email actualizat cu succes pentru idKeycloak={}.", idKeycloak);
        return toUserMeDto(user);
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
                .orElseThrow(() -> new UserNotFoundException("Nu s-a găsit utilizatorul cu idKeycloak=" + idKeycloak));
    }

    private UserMeDto toUserMeDto(User user) {
        return new UserMeDto(
                user.getId(),
                user.getNume(),
                user.getPrenume(),
                user.getMail(),
                user.getRol().getDenumire(),
                user.getFacultate(),
                user.getStareCont().getDenumire()
        );
    }
}
