package com.example.akadion.controller;

import com.example.akadion.dto.AkyChatResponseDto;
import com.example.akadion.dto.ConversatieDTO;
import com.example.akadion.dto.ConversatiiPaginateDto;
import com.example.akadion.dto.IstoricMesajeDto;
import com.example.akadion.dto.MesajChatDTO;
import com.example.akadion.dto.NouaIntrebareRequest;
import com.example.akadion.dto.RagRaspunsResponse;
import com.example.akadion.entity.Conversatie;
import com.example.akadion.entity.MesajChat;
import com.example.akadion.entity.User;
import com.example.akadion.exception.UserNotFoundException;
import com.example.akadion.repository.UserRepository;
import com.example.akadion.service.ConversatieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'PROFESOR')")
public class ConversatieController {

    private final ConversatieService conversatieService;
    private final UserRepository userRepository;

    @GetMapping("/conversatii")
    public ConversatiiPaginateDto getAllConversatii(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        return conversatieService.obtineToateConversatiileActive(user.getId(), page, size);
    }

    @GetMapping("/cursuri/{cursId}/conversatii")
    public ConversatiiPaginateDto getConversatii(
            @PathVariable Long cursId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        return conversatieService.obtineConversatiiActive(user.getId(), cursId, page, size);
    }

    @PostMapping("/cursuri/{cursId}/conversatii/mesaje")
    public RagRaspunsResponse creareConversatieSiMesaj(
            @PathVariable Long cursId,
            @Valid @RequestBody NouaIntrebareRequest request,
            @AuthenticationPrincipal OidcUser oidcUser) {
        
        User user = getLoggedUser(oidcUser);
        
        // Pas 1
        MesajChat intrebare = conversatieService.salveazaIntrebare(null, user.getId(), cursId, request.intrebare());
        
        // Pas 2
        AkyChatResponseDto ragResponse = conversatieService.obtineRaspunsRag(
                intrebare.getConversatie().getId(), user.getId(), request.intrebare());
        
        // Pas 3
        MesajChat raspuns = conversatieService.salveazaRaspuns(intrebare.getId(), ragResponse);
        
        return new RagRaspunsResponse(
                intrebare.getConversatie().getId(),
                new MesajChatDTO(raspuns.getId(), raspuns.getRol(), raspuns.getContinut(), raspuns.getSurseFolosite(), raspuns.getCreatedAt(), raspuns.getAreRaspuns())
        );
    }

    @GetMapping("/conversatii/{id}/mesaje")
    public IstoricMesajeDto getIstoric(
            @PathVariable Long id,
            @RequestParam(required = false) Long inainteDe,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        return conversatieService.obtineIstoric(user.getId(), id, inainteDe, limit);
    }

    @PostMapping("/conversatii/{id}/mesaje")
    public MesajChatDTO adaugaMesaj(
            @PathVariable Long id,
            @Valid @RequestBody NouaIntrebareRequest request,
            @AuthenticationPrincipal OidcUser oidcUser) {
        
        User user = getLoggedUser(oidcUser);
        
        // Pas 1
        MesajChat intrebare = conversatieService.salveazaIntrebare(id, user.getId(), null, request.intrebare());
        
        // Pas 2
        AkyChatResponseDto ragResponse = conversatieService.obtineRaspunsRag(
                id, user.getId(), request.intrebare());
        
        // Pas 3
        MesajChat raspuns = conversatieService.salveazaRaspuns(intrebare.getId(), ragResponse);
        
        return new MesajChatDTO(raspuns.getId(), raspuns.getRol(), raspuns.getContinut(), raspuns.getSurseFolosite(), raspuns.getCreatedAt(), raspuns.getAreRaspuns());
    }

    @PostMapping("/conversatii/mesaje/{mesajId}/retry")
    public MesajChatDTO retryMesaj(
            @PathVariable Long mesajId,
            @AuthenticationPrincipal OidcUser oidcUser) {
        
        User user = getLoggedUser(oidcUser);
        
        // Pas 2: Reluam apelul RAG
        AkyChatResponseDto ragResponse = conversatieService.retryMesaj(mesajId, user.getId());
        
        // Pas 3: Salvam
        MesajChat raspuns = conversatieService.salveazaRaspuns(mesajId, ragResponse);
        
        return new MesajChatDTO(raspuns.getId(), raspuns.getRol(), raspuns.getContinut(), raspuns.getSurseFolosite(), raspuns.getCreatedAt(), raspuns.getAreRaspuns());
    }

    @DeleteMapping("/conversatii/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stergeConversatie(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        conversatieService.stergeConversatie(user.getId(), id);
    }

    private User getLoggedUser(OidcUser oidcUser) {
        return userRepository.findByIdKeycloak(oidcUser.getSubject())
                .orElseThrow(() -> new UserNotFoundException("Utilizatorul autentificat nu are cont local."));
    }
}
