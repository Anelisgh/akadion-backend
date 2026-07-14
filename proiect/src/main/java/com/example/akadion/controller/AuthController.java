package com.example.akadion.controller;

import com.example.akadion.dto.CompleteProfileRequestDto;
import com.example.akadion.service.CompleteProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

// Acest controller gestionează acțiunile legate de contul utilizatorului logat.
// Folosim RestController pentru a-i spune lui Spring că returnăm date direct (de tip JSON / răspunsuri HTTP simple).
@RestController
@RequestMapping("/api/auth") // Toate căile din acest controller vor începe cu "/api/auth"
@RequiredArgsConstructor
public class AuthController {

    private final CompleteProfileService completeProfileService;

    // Acest endpoint este apelat de frontend imediat după ce utilizatorul s-a înregistrat în Keycloak.
    // Utilizatorul este trimis aici pentru a-și introduce numele, prenumele, facultatea și rolul dorit.
    // Apel: POST /api/auth/complete-profile
    @PostMapping("/complete-profile")
    @ResponseStatus(HttpStatus.OK) // Răspundem cu 200 OK dacă profilul a fost completat cu succes
    public void completeProfile(
            // @Valid: Verifică automat dacă datele trimise respectă regulile de validare (ex: nume nevid, etc.)
            // @RequestBody: Preia JSON-ul trimis de frontend și îl transformă în obiectul Java CompleteProfileRequestDto
            @Valid @RequestBody CompleteProfileRequestDto dto,
            
            // @AuthenticationPrincipal: Preia automat datele utilizatorului logat în acest moment din sesiunea OIDC/Keycloak.
            @AuthenticationPrincipal OidcUser oidcUser) {
        
        // Extragem UUID-ul unic din token-ul de login Keycloak.
        String sub = oidcUser.getSubject();
        
        // Trimitem UUID-ul și datele din formular către serviciu pentru a fi salvate în baza de date.
        completeProfileService.completeaza(sub, dto);
    }
}
