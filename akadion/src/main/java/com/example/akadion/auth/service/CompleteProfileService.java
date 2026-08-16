package com.example.akadion.auth.service;

import com.example.akadion.admin.entity.NumeTabelAudit;
import com.example.akadion.admin.entity.OperatieAudit;
import com.example.akadion.admin.service.AuditLogService;
import com.example.akadion.auth.dto.CompleteProfileRequestDto;
import com.example.akadion.auth.dto.CompleteProfileResponseDto;
import com.example.akadion.common.entity.NumeRol;
import com.example.akadion.common.entity.NumeStareCont;
import com.example.akadion.common.entity.Rol;
import com.example.akadion.common.entity.StareCont;
import com.example.akadion.common.entity.User;
import com.example.akadion.exception.EmailDuplicatException;
import com.example.akadion.exception.ForbiddenOperationException;
import com.example.akadion.exception.InvalidUserStateException;
import com.example.akadion.exception.ResursaNegasitaException;
import com.example.akadion.common.repository.RolRepository;
import com.example.akadion.common.repository.StareContRepository;
import com.example.akadion.common.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompleteProfileService {

    private static final String INCOMPLETE_STATE = NumeStareCont.INCOMPLET.name();
    private static final String PENDING_STATE = NumeStareCont.PENDING.name();
    private static final String REJECTED_STATE = NumeStareCont.RESPINS.name();

    private final UserRepository userRepository;
    private final RolRepository rolRepository;
    private final StareContRepository stareContRepository;
    private final AuditLogService auditLogService;

    // Adnotația @Transactional garantează că toate operațiunile din această metodă se execută ca o singură tranzacție.
    // Dacă ceva dă eroare pe parcurs, toate modificările din baza de date se anulează automat (rollback).
    @Transactional
    public CompleteProfileResponseDto completeaza(String subKeycloak, String email, CompleteProfileRequestDto dto) {
        String normalizedSub = normalizeRequired(subKeycloak, "Identitatea utilizatorului autentificat este invalidă.");
        String normalizedEmail = normalizeRequired(email, "Tokenul utilizatorului nu conține un email valid.").toLowerCase(Locale.ROOT);

        User user = userRepository.findByIdKeycloak(normalizedSub)
                .orElseThrow(() -> new ResursaNegasitaException(
                        "Utilizatorul autentificat cu sub=" + normalizedSub + " nu are cont local."));

        updateExistingUser(user, normalizedEmail, dto);

        User savedUser = userRepository.save(user);

        auditLogService.inregistreaza(
                NumeTabelAudit.APP_USER,
                savedUser.getId(),
                OperatieAudit.COMPLETARE_PROFIL,
                null,
                Map.of("rolDorit", dto.rolDorit(), "facultate", dto.facultate())
        );
        log.info("Profil salvat pentru sub={} în starea PENDING.", normalizedSub);
        return toResponse(savedUser);
    }

    private void updateExistingUser(User user, String email, CompleteProfileRequestDto dto) {
        String stareCurenta = user.getStareCont().getDenumire();
        Set<String> stariAcceptate = Set.of(INCOMPLETE_STATE, REJECTED_STATE);
        if (!stariAcceptate.contains(stareCurenta)) {
            throw new InvalidUserStateException(
                    "Completarea profilului este permisă doar pentru conturile INCOMPLET sau RESPINS (stare curentă: " + stareCurenta + ").");
        }

        userRepository.findByMail(email)
                .filter(existingUser -> !existingUser.getIdKeycloak().equals(user.getIdKeycloak()))
                .ifPresent(existingUser -> {
                    throw duplicateEmailException(existingUser);
                });

        applyProfileData(user, email, dto);
    }

    private void applyProfileData(User user, String trustedEmail, CompleteProfileRequestDto dto) {
        // Pasul 2: Căutăm în DB starea "PENDING". Contul va trece în această stare după ce salvăm datele.
        StareCont pending = stareContRepository.findByDenumire(PENDING_STATE)
                .orElseThrow(() -> new IllegalStateException("Starea PENDING lipsește din DB — verifică DataSeeder"));

        // Pasul 4: Căutăm rolul ales de utilizator în baza de date (STUDENT sau PROFESOR).
        Rol rol = rolRepository.findByDenumire(dto.rolDorit())
                .orElseThrow(() -> new IllegalStateException("Rolul '" + dto.rolDorit() + "' lipsește din DB — verifică DataSeeder"));

        if (NumeRol.ADMIN.name().equals(rol.getDenumire())) {
            throw new ForbiddenOperationException("Rolul ADMIN nu poate fi solicitat din formularul de profil.");
        }

        log.info("Completare profil pentru sub={}: nume={}, prenume={}, rolDorit={}",
                user.getIdKeycloak(), dto.nume(), dto.prenume(), dto.rolDorit());

        // Pasul 5: Actualizăm datele utilizatorului din baza de date cu cele primite din formular.
        user.setNume(dto.nume());
        user.setPrenume(dto.prenume());
        user.setMail(trustedEmail);
        user.setFacultate(dto.facultate());
        user.setRol(rol); // Îi atribuim rolul
        user.setStareCont(pending); // Îl punem în starea PENDING (în așteptare aprobare admin)
    }

    private EmailDuplicatException duplicateEmailException(User existingUser) {
        return new EmailDuplicatException(
                "Există deja un cont local asociat cu emailul " + existingUser.getMail() + ".");
    }

    private String normalizeRequired(String value, String errorMessage) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new ForbiddenOperationException(errorMessage);
        }

        return normalized;
    }

    private CompleteProfileResponseDto toResponse(User user) {
        return new CompleteProfileResponseDto(
                user.getId(),
                user.getNume(),
                user.getPrenume(),
                user.getMail(),
                user.getFacultate(),
                user.getRolDenumire(),
                user.getStareCont() != null ? user.getStareCont().getDenumire() : null,
                user.getCreatedAt(),
                "Profilul a fost salvat și trimis pentru aprobare."
        );
    }
}
