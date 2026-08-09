package com.example.akadion.controller;

import com.example.akadion.dto.SaptamanaRequestDto;
import com.example.akadion.dto.SaptamanaResponseDto;
import com.example.akadion.entity.User;
import com.example.akadion.exception.UserNotFoundException;
import com.example.akadion.repository.UserRepository;
import com.example.akadion.service.SaptamanaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profesor")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PROFESOR', 'ADMIN')")
public class SaptamanaProfesorController {

    private final SaptamanaService saptamanaService;
    private final UserRepository userRepository;

    @GetMapping("/cursuri/{cursId}/saptamani")
    public List<SaptamanaResponseDto> listaSaptamani(
            @PathVariable Long cursId,
            @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        String role = user.getRol() != null ? user.getRol().getDenumire() : "";
        return saptamanaService.listaSaptamani(cursId, user.getId(), role);
    }

    @PostMapping("/cursuri/{cursId}/saptamani")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PROFESOR')")
    public SaptamanaResponseDto adaugaSaptamana(
            @PathVariable Long cursId,
            @Valid @RequestBody SaptamanaRequestDto dto,
            @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        return saptamanaService.adaugaSaptamana(cursId, user.getId(), dto);
    }

    @PutMapping("/saptamani/{id}")
    @PreAuthorize("hasRole('PROFESOR')")
    public SaptamanaResponseDto modificaSaptamana(
            @PathVariable Long id,
            @Valid @RequestBody SaptamanaRequestDto dto,
            @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        return saptamanaService.modificaSaptamana(id, user.getId(), dto);
    }

    @DeleteMapping("/saptamani/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('PROFESOR')")
    public void stergeUltimaSaptamana(
            @PathVariable Long id,
            @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        saptamanaService.stergeUltimaSaptamana(id, user.getId());
    }

    private User getLoggedUser(OidcUser oidcUser) {
        return userRepository.findByIdKeycloak(oidcUser.getSubject())
                .orElseThrow(() -> new UserNotFoundException("Utilizatorul autentificat nu are cont local."));
    }
}
