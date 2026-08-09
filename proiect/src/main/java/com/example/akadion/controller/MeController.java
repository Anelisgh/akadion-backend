package com.example.akadion.controller;

import com.example.akadion.dto.UpdateEmailRequestDto;
import com.example.akadion.dto.UpdateProfileRequestDto;
import com.example.akadion.dto.UserMeDto;
import com.example.akadion.exception.UserNotFoundException;
import com.example.akadion.repository.UserRepository;
import com.example.akadion.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

// Acest controller expune date despre utilizatorul logat în mod curent.
@RestController
@RequestMapping("/api/auth/me")
@RequiredArgsConstructor
public class MeController {

    private final UserRepository userRepository;
    private final UserProfileService userProfileService;

    // Acest endpoint este apelat de frontend (React) la pornirea aplicației (sau la refresh).
    // Rolul lui este să răspundă la întrebarea: "Cine este utilizatorul logat acum și care este starea contului lui?"
    // Răspunsul permite frontend-ului să știe dacă să-i arate meniul normal, pagina de completat profil, sau mesajul de așteptare.
    // Apel: GET /api/auth/me
    @GetMapping
    public UserMeDto getMe(@AuthenticationPrincipal OidcUser oidcUser) {
        String sub = oidcUser.getSubject();

        return userRepository.findByIdKeycloak(sub)
                .map(user -> new UserMeDto(
                        user.getId(),
                        user.getNume(),
                        user.getPrenume(),
                        user.getMail(),
                        user.getRol() != null ? user.getRol().getDenumire() : null,
                        user.getFacultate(),
                        user.getStareCont().getDenumire()
                ))
                .orElseThrow(() -> new UserNotFoundException(
                        "Utilizatorul autentificat cu sub=" + sub + " nu are cont local."));
    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    public UserMeDto updateProfile(
            @Valid @RequestBody UpdateProfileRequestDto dto,
            @AuthenticationPrincipal OidcUser oidcUser) {
        return userProfileService.updateProfile(oidcUser.getSubject(), dto);
    }

    @PutMapping("/email")
    @ResponseStatus(HttpStatus.OK)
    public UserMeDto updateEmail(
            @Valid @RequestBody UpdateEmailRequestDto dto,
            @AuthenticationPrincipal OidcUser oidcUser) {
        return userProfileService.updateEmail(oidcUser.getSubject(), dto);
    }

    @PostMapping("/request-password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(@AuthenticationPrincipal OidcUser oidcUser) {
        userProfileService.requestPasswordReset(oidcUser.getSubject());
    }
}
