package com.example.akadion.controller;

import com.example.akadion.dto.CompleteProfileRequestDto;
import com.example.akadion.service.CompleteProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final CompleteProfileService completeProfileService;

    /**
     * Completează profilul unui utilizator autentificat (în stare INCOMPLET sau RESPINS).
     * Se apelează după ce contul a fost creat nativ în Keycloak.
     */
    @PostMapping("/complete-profile")
    @ResponseStatus(HttpStatus.OK)
    public void completeProfile(@Valid @RequestBody CompleteProfileRequestDto dto,
                                @AuthenticationPrincipal OidcUser oidcUser) {
        String sub = oidcUser.getSubject();
        completeProfileService.completeaza(sub, dto);
    }
}
