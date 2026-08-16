package com.example.akadion.auth.controller;

import com.example.akadion.auth.dto.CompleteProfileRequestDto;
import com.example.akadion.auth.dto.CompleteProfileResponseDto;
import com.example.akadion.auth.service.CompleteProfileService;
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

    @PostMapping("/complete-profile")
    @ResponseStatus(HttpStatus.OK)
    public CompleteProfileResponseDto completeProfile(
            @Valid @RequestBody CompleteProfileRequestDto dto,
            @AuthenticationPrincipal OidcUser oidcUser) {
        String sub = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        return completeProfileService.completeaza(sub, email, dto);
    }
}
