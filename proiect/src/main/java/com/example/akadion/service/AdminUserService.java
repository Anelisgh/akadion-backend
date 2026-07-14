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

// Acest serviciu conține toate regulile de afaceri (business logic) aplicate utilizatorilor de către administrator.
// Aici se face listarea cererilor, aprobarea, respingerea, dezactivarea și reactivarea conturilor.
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final StareContRepository stareContRepository;
    private final KeycloakAdminService keycloakAdminService; // Conexiunea cu Keycloak

    // 1. Listează toate cererile de utilizatori care sunt în starea PENDING (așteaptă aprobare).
    // @Transactional(readOnly = true) îi spune bazei de date că doar citim informații (ceea ce este mai rapid și sigur).
    @Transactional(readOnly = true)
    public List<UserPendingDto> listaCereriPending() {
        return userRepository.findByStareCont_Denumire("PENDING")
                .stream()
                // Transformăm fiecare utilizator din baza de date într-un obiect simplu de trimis (DTO)
                .map(user -> new UserPendingDto(
                        user.getId(),
                        user.getNume(),
                        user.getPrenume(),
                        user.getMail(),
                        user.getFacultate(),
                        // Dacă utilizatorul nu are rol (deși nu ar trebui în starea PENDING), punem null
                        user.getRol() != null ? user.getRol().getDenumire() : null,
                        // Citim numărul de respingeri anterioare (dacă este null, punem implicit 0)
                        user.getNrRespingeri() != null ? user.getNrRespingeri() : 0
                ))
                .toList();
    }

    // 2. Aprobă (acceptă) contul unui utilizator aflat în starea PENDING.
    @Transactional
    public void acceptaUser(Long userId) {
        // Căutăm utilizatorul după ID-ul primit
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Regula A: Nu putem aproba decât utilizatori care au starea PENDING.
        if (!"PENDING".equals(user.getStareCont().getDenumire())) {
            throw new InvalidUserStateException(
                    "Utilizatorul " + userId + " nu are starea PENDING (starea curentă: "
                    + user.getStareCont().getDenumire() + ")");
        }

        // Căutăm starea "ACTIV" în DB
        StareCont activ = stareContRepository.findByDenumire("ACTIV")
                .orElseThrow(() -> new IllegalStateException("STARE_CONT 'ACTIV' lipsă din DB — verifică DataSeeder"));

        // Modificăm starea contului în ACTIV (rolul a fost deja setat în Complete-Profile).
        // Decizia KISS: Nu mai facem niciun apel către Keycloak pentru roluri; baza de date este sursa de adevăr!
        user.setStareCont(activ);
        userRepository.save(user);

        log.info("User acceptat în DB: userId={}, mail={}, rol={}", 
                userId, user.getMail(), user.getRol() != null ? user.getRol().getDenumire() : "FĂRĂ ROL");
    }

    // 3. Respinge contul unui utilizator aflat în starea PENDING.
    @Transactional
    public void respingeUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Regula B: Doar utilizatorii PENDING pot fi respinși.
        if (!"PENDING".equals(user.getStareCont().getDenumire())) {
            throw new InvalidUserStateException(
                    "Utilizatorul " + userId + " nu are starea PENDING — nu poate fi respins.");
        }

        // Căutăm starea "RESPINS" în DB
        StareCont respins = stareContRepository.findByDenumire("RESPINS")
                .orElseThrow(() -> new IllegalStateException("STARE_CONT 'RESPINS' lipsă din DB — verifică DataSeeder"));

        // Modificăm starea contului în RESPINS și incrementăm numărul de respingeri cu +1.
        // Utilizatorul se va putea loga la loc în Keycloak dar filtrul îl va bloca și îl va trimite să își corecteze datele.
        user.setStareCont(respins);
        user.setNrRespingeri((user.getNrRespingeri() != null ? user.getNrRespingeri() : 0) + 1);
        userRepository.save(user);

        log.info("Cerere respinsă în DB: userId={}, mail={}, nrRespingeriCurent={}", 
                userId, user.getMail(), user.getNrRespingeri());
    }

    // 4. Dezactivează definitiv un utilizator care în prezent este ACTIV.
    @Transactional
    public void dezactiveazaUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Regula C: Doar utilizatorii în starea ACTIV pot fi dezactivați.
        if (!"ACTIV".equals(user.getStareCont().getDenumire())) {
            throw new InvalidUserStateException(
                    "Doar utilizatorii cu starea ACTIV pot fi dezactivați.");
        }

        // Apelăm Keycloak Admin API pentru a dezactiva contul la nivelul serverului de autentificare.
        // Asta previne logarea fizică a utilizatorului.
        keycloakAdminService.dezactiveazaUser(user.getIdKeycloak());

        // Căutăm starea "INACTIV" în DB
        StareCont inactiv = stareContRepository.findByDenumire("INACTIV")
                .orElseThrow(() -> new IllegalStateException("STARE_CONT 'INACTIV' lipsă din DB — verifică DataSeeder"));
        
        // Modificăm starea locală a contului în INACTIV
        user.setStareCont(inactiv);
        userRepository.save(user);

        log.info("User dezactivat local și în Keycloak: userId={}, sub={}", userId, user.getIdKeycloak());
    }

    // 5. Reactivează (activează) un utilizator care fusese marcat anterior ca INACTIV.
    @Transactional
    public void activeazaUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Regula D: Doar conturile marcate INACTIV pot fi reactivate.
        if (!"INACTIV".equals(user.getStareCont().getDenumire())) {
            throw new InvalidUserStateException(
                    "Doar utilizatorii cu starea INACTIV pot fi reactivați.");
        }

        // Deblocăm (reactivăm) contul direct în Keycloak (enabled = true).
        keycloakAdminService.reactiveazaUser(user.getIdKeycloak());

        // Căutăm starea "ACTIV" în DB
        StareCont activ = stareContRepository.findByDenumire("ACTIV")
                .orElseThrow(() -> new IllegalStateException("STARE_CONT 'ACTIV' lipsă din DB — verifică DataSeeder"));
        
        // Redăm starea ACTIV contului local
        user.setStareCont(activ);
        userRepository.save(user);

        log.info("User reactivat local și în Keycloak: userId={}, sub={}", userId, user.getIdKeycloak());
    }
}
