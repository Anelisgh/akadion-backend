package com.example.akadion.controller;

import com.example.akadion.dto.UserMeDto;
import com.example.akadion.exception.UserNotFoundException;
import com.example.akadion.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Acest controller expune date despre utilizatorul logat în mod curent.
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class MeController {

    private final UserRepository userRepository;

    // Acest endpoint este apelat de frontend (React) la pornirea aplicației (sau la refresh).
    // Rolul lui este să răspundă la întrebarea: "Cine este utilizatorul logat acum și care este starea contului lui?"
    // Răspunsul permite frontend-ului să știe dacă să-i arate meniul normal, pagina de completat profil, sau mesajul de așteptare.
    // Apel: GET /api/auth/me
    @GetMapping("/me")
    public UserMeDto me(
            // Preia automat datele utilizatorului conectat acum din sesiunea de securitate
            @AuthenticationPrincipal OidcUser oidcUser) {
        
        // Extragem UUID-ul unic oferit de Keycloak
        String sub = oidcUser.getSubject();

        // Căutăm în baza de date locală rândul utilizatorului pe baza UUID-ului din Keycloak
        return userRepository.findByIdKeycloak(sub)
                // Dacă îl găsim, îi mapăm (transformăm) datele într-un pachet simplu de trimis (DTO)
                .map(user -> new UserMeDto(
                        user.getId(),
                        user.getNume(),
                        user.getPrenume(),
                        user.getMail(),
                        // Dacă utilizatorul nu are încă rol (stare INCOMPLET), punem null
                        user.getRol() != null ? user.getRol().getDenumire() : null,
                        // Trimitem starea contului (ex: INCOMPLET, PENDING, ACTIV, RESPINS, INACTIV)
                        user.getStareCont().getDenumire()
                ))
                // Dacă nu există deloc utilizatorul în DB-ul local, aruncăm o eroare personalizată
                .orElseThrow(() -> new UserNotFoundException(0L));
    }
}
