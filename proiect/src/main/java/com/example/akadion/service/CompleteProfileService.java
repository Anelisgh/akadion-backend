package com.example.akadion.service;

import com.example.akadion.dto.CompleteProfileRequestDto;
import com.example.akadion.entity.Rol;
import com.example.akadion.entity.StareCont;
import com.example.akadion.entity.User;
import com.example.akadion.exception.UserNotFoundException;
import com.example.akadion.repository.RolRepository;
import com.example.akadion.repository.StareContRepository;
import com.example.akadion.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompleteProfileService {

    private final UserRepository userRepository;
    private final RolRepository rolRepository;
    private final StareContRepository stareContRepository;

    // Adnotația @Transactional garantează că toate operațiunile din această metodă se execută ca o singură tranzacție.
    // Dacă ceva dă eroare pe parcurs, toate modificările din baza de date se anulează automat (rollback).
    @Transactional
    public void completeaza(String subKeycloak, CompleteProfileRequestDto dto) {
        // Pasul 1: Căutăm utilizatorul în DB după UUID-ul din Keycloak.
        User user = userRepository.findByIdKeycloak(subKeycloak)
                .orElseThrow(() -> new UserNotFoundException(0L));

        // Pasul 2: Căutăm în DB starea "PENDING". Contul va trece în această stare după ce salvăm datele.
        StareCont pending = stareContRepository.findByDenumire("PENDING")
                .orElseThrow(() -> new IllegalStateException("Starea PENDING lipsește din DB — verifică DataSeeder"));

        // Pasul 3: Regula de business: completarea profilului este permisă doar dacă utilizatorul
        // este la prima încercare (INCOMPLET) sau dacă a fost respins anterior de un admin (RESPINS) și vrea să corecteze datele.
        String stareCurenta = user.getStareCont().getDenumire();
        if (!"INCOMPLET".equals(stareCurenta) && !"RESPINS".equals(stareCurenta)) {
            throw new com.example.akadion.exception.InvalidUserStateException(
                    "Completarea profilului este permisă doar pentru conturile INCOMPLET sau RESPINS (stare curentă: " + stareCurenta + ").");
        }

        // Pasul 4: Căutăm rolul ales de utilizator în baza de date (STUDENT sau PROFESOR).
        Rol rol = rolRepository.findByDenumire(dto.rolDorit())
                .orElseThrow(() -> new IllegalStateException("Rolul '" + dto.rolDorit() + "' lipsește din DB — verifică DataSeeder"));

        log.info("Completare profil pentru sub={}: nume={}, prenume={}, rolDorit={}",
                subKeycloak, dto.nume(), dto.prenume(), dto.rolDorit());

        // Pasul 5: Actualizăm datele utilizatorului din baza de date cu cele primite din formular.
        user.setNume(dto.nume());
        user.setPrenume(dto.prenume());
        user.setFacultate(dto.facultate());
        user.setRol(rol); // Îi atribuim rolul
        user.setStareCont(pending); // Îl punem în starea PENDING (în așteptare aprobare admin)

        // Pasul 6: Salvăm modificările în baza de date.
        userRepository.save(user);
        log.info("Profil salvat pentru sub={} în starea PENDING.", subKeycloak);
    }
}
