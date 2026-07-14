package com.example.akadion.service;

import com.example.akadion.dto.UserPendingDto;
import com.example.akadion.entity.StareCont;
import com.example.akadion.entity.User;
import com.example.akadion.exception.InvalidUserStateException;
import com.example.akadion.exception.UserNotFoundException;
import com.example.akadion.repository.StareContRepository;
import com.example.akadion.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final StareContRepository stareContRepository;
    private final KeycloakAdminService keycloakAdminService;

    /**
     * Returnează toate cererile PENDING, folosind direct câmpul `nrRespingeri` stocat în DB.
     */
    @Transactional(readOnly = true)
    public List<UserPendingDto> listaCereriPending() {
        return userRepository.findByStareCont_Denumire("PENDING")
                .stream()
                .map(user -> new UserPendingDto(
                        user.getId(),
                        user.getNume(),
                        user.getPrenume(),
                        user.getMail(),
                        user.getFacultate(),
                        user.getRol() != null ? user.getRol().getDenumire() : null,
                        user.getNrRespingeri() != null ? user.getNrRespingeri() : 0
                ))
                .toList();
    }

    /**
     * Acceptă o cerere PENDING.
     * Devine o operație pură de DB, deoarece rolul a fost deja setat în etapa de Complete-Profile
     * și nu mai stocăm rolul în Keycloak (decizie KISS).
     */
    @Transactional
    public void acceptaUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!"PENDING".equals(user.getStareCont().getDenumire())) {
            throw new InvalidUserStateException(
                    "Utilizatorul " + userId + " nu are starea PENDING (starea curentă: "
                    + user.getStareCont().getDenumire() + ")");
        }

        StareCont activ = stareContRepository.findByDenumire("ACTIV")
                .orElseThrow(() -> new IllegalStateException("STARE_CONT 'ACTIV' lipsă din DB — verifică DataSeeder"));

        user.setStareCont(activ);
        userRepository.save(user);

        log.info("User acceptat în DB: userId={}, mail={}, rol={}", 
                userId, user.getMail(), user.getRol() != null ? user.getRol().getDenumire() : "FĂRĂ ROL");
    }

    /**
     * Respinge o cerere PENDING.
     * Schimbă starea în RESPINS și incrementează contorul de respingeri. Keycloak nu se atinge.
     */
    @Transactional
    public void respingeUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!"PENDING".equals(user.getStareCont().getDenumire())) {
            throw new InvalidUserStateException(
                    "Utilizatorul " + userId + " nu are starea PENDING — nu poate fi respins.");
        }

        StareCont respins = stareContRepository.findByDenumire("RESPINS")
                .orElseThrow(() -> new IllegalStateException("STARE_CONT 'RESPINS' lipsă din DB — verifică DataSeeder"));

        user.setStareCont(respins);
        user.setNrRespingeri((user.getNrRespingeri() != null ? user.getNrRespingeri() : 0) + 1);
        userRepository.save(user);

        log.info("Cerere respinsă în DB: userId={}, mail={}, nrRespingeriCurent={}", 
                userId, user.getMail(), user.getNrRespingeri());
    }

    /**
     * Dezactivează un utilizator ACTIV.
     * Setează starea INACTIV în DB și dezactivează contul corespunzător în Keycloak.
     */
    @Transactional
    public void dezactiveazaUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!"ACTIV".equals(user.getStareCont().getDenumire())) {
            throw new InvalidUserStateException(
                    "Doar utilizatorii cu starea ACTIV pot fi dezactivați.");
        }

        keycloakAdminService.dezactiveazaUser(user.getIdKeycloak());

        StareCont inactiv = stareContRepository.findByDenumire("INACTIV")
                .orElseThrow(() -> new IllegalStateException("STARE_CONT 'INACTIV' lipsă din DB — verifică DataSeeder"));
        user.setStareCont(inactiv);
        userRepository.save(user);

        log.info("User dezactivat local și în Keycloak: userId={}, sub={}", userId, user.getIdKeycloak());
    }

    /**
     * Activează (reactivează) un utilizator INACTIV.
     * Setează starea ACTIV în DB și reactivează contul corespunzător în Keycloak (enabled = true).
     */
    @Transactional
    public void activeazaUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!"INACTIV".equals(user.getStareCont().getDenumire())) {
            throw new InvalidUserStateException(
                    "Doar utilizatorii cu starea INACTIV pot fi reactivați.");
        }

        keycloakAdminService.reactiveazaUser(user.getIdKeycloak());

        StareCont activ = stareContRepository.findByDenumire("ACTIV")
                .orElseThrow(() -> new IllegalStateException("STARE_CONT 'ACTIV' lipsă din DB — verifică DataSeeder"));
        user.setStareCont(activ);
        userRepository.save(user);

        log.info("User reactivat local și în Keycloak: userId={}, sub={}", userId, user.getIdKeycloak());
    }
}
