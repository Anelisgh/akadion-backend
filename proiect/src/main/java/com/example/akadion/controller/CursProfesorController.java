package com.example.akadion.controller;

import com.example.akadion.dto.*;
import com.example.akadion.entity.User;
import com.example.akadion.exception.UserNotFoundException;
import com.example.akadion.repository.UserRepository;
import com.example.akadion.service.CursService;
import com.example.akadion.service.RagChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profesor/cursuri")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PROFESOR', 'ADMIN')")
public class CursProfesorController {

    private final CursService cursService;
    private final UserRepository userRepository;
    private final RagChatService ragChatService;

    @GetMapping
    public List<CursResponseDto> listaCursuri(@AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        String role = user.getRol() != null ? user.getRol().getDenumire() : "";
        if ("ADMIN".equals(role)) {
            return cursService.listaToateCursurile();
        } else {
            return cursService.listaCursuriProprii(user.getId());
        }
    }

    @GetMapping("/{id}")
    public CursResponseDto getCursById(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        String role = user.getRol() != null ? user.getRol().getDenumire() : "";
        return cursService.getCursById(id, user.getId(), role);
    }

    @GetMapping("/{id}/studenti")
    public List<StudentCursDto> listaStudentiActivi(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        String role = user.getRol() != null ? user.getRol().getDenumire() : "";
        return cursService.listaStudentiActivi(id, user.getId(), role);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PROFESOR')")
    public CursResponseDto creazaCurs(@Valid @RequestBody CursRequestDto dto, @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        return cursService.creazaCurs(user.getId(), dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PROFESOR')")
    public CursResponseDto modificaCurs(@PathVariable Long id, @Valid @RequestBody CursRequestDto dto, @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        return cursService.modificaCurs(id, user.getId(), dto);
    }

    @PatchMapping("/{id}/dezactiveaza")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('PROFESOR')")
    public void dezactiveazaCurs(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        cursService.dezactiveazaCurs(id, user.getId());
    }

    @PatchMapping("/{id}/activeaza")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('PROFESOR')")
    public void activeazaCurs(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        cursService.activeazaCurs(id, user.getId());
    }

    @PostMapping("/{cursId}/chat")
    @PreAuthorize("hasRole('PROFESOR')")
    public AkyChatResponseDto chatAkyProfesor(
            @PathVariable Long cursId,
            @org.springframework.validation.annotation.Validated @jakarta.validation.Valid @RequestBody AkyChatRequestDto request,
            @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        
        // Verificăm dacă profesorul are acces la acest curs (dacă este autorul lui)
        cursService.getCursById(cursId, user.getId(), "PROFESOR");
        
        // Dacă a trecut, înseamnă că are drepturi pe curs, chemăm RAG
        return ragChatService.intreabaAky(user.getId(), cursId, request);
    }

    private User getLoggedUser(OidcUser oidcUser) {
        return userRepository.findByIdKeycloak(oidcUser.getSubject())
                .orElseThrow(() -> new UserNotFoundException("Utilizatorul autentificat nu are cont local."));
    }
}
