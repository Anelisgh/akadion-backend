package com.example.akadion.auth.controller;

import com.example.akadion.auth.dto.UpdateEmailRequestDto;
import com.example.akadion.auth.dto.UpdateProfileRequestDto;
import com.example.akadion.auth.dto.UserMeDto;
import com.example.akadion.exception.ResursaNegasitaException;
import com.example.akadion.common.repository.UserRepository;
import com.example.akadion.auth.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/me")
@RequiredArgsConstructor
public class MeController {

    private final UserRepository userRepository;
    private final UserProfileService userProfileService;

    @GetMapping
    public UserMeDto getMe(@AuthenticationPrincipal OidcUser oidcUser) {
        String sub = oidcUser.getSubject();

        return userRepository.findByIdKeycloak(sub)
                .map(user -> new UserMeDto(
                        user.getId(),
                        user.getNume(),
                        user.getPrenume(),
                        user.getMail(),
                        user.getRolDenumire(),
                        user.getFacultate(),
                        user.getStareCont().getDenumire()
                ))
                .orElseThrow(() -> new ResursaNegasitaException(
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
