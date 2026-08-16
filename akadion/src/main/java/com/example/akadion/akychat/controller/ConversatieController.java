package com.example.akadion.akychat.controller;

import com.example.akadion.akychat.dto.AkyChatResponseDto;
import com.example.akadion.akychat.dto.ConversatiiPaginateDto;
import com.example.akadion.akychat.dto.IstoricMesajeDto;
import com.example.akadion.akychat.dto.MesajChatDto;
import com.example.akadion.akychat.dto.NouaIntrebareRequestDto;
import com.example.akadion.akychat.dto.RagRaspunsResponseDto;
import com.example.akadion.akychat.entity.MesajChat;
import com.example.akadion.akychat.service.ConversatieService;
import com.example.akadion.auth.security.CurrentUser;
import com.example.akadion.auth.security.CurrentUserDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ConversatieController {

    private final ConversatieService conversatieService;

    @GetMapping("/conversatii")
    @PreAuthorize("hasAnyRole('STUDENT', 'PROFESOR')")
    public ConversatiiPaginateDto getAllConversatii(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser CurrentUserDto user) {
        return conversatieService.obtineToateConversatiileActive(user.id(), page, size);
    }

    @GetMapping("/cursuri/{cursId}/conversatii")
    @PreAuthorize("hasAnyRole('STUDENT', 'PROFESOR')")
    public ConversatiiPaginateDto getConversatii(
            @PathVariable Long cursId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser CurrentUserDto user) {
        return conversatieService.obtineConversatiiActive(user.id(), cursId, page, size);
    }

    @PostMapping("/cursuri/{cursId}/conversatii/mesaje")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('STUDENT', 'PROFESOR')")
    public RagRaspunsResponseDto creareConversatieSiMesaj(
            @PathVariable Long cursId,
            @Valid @RequestBody NouaIntrebareRequestDto request,
            @CurrentUser CurrentUserDto user) {
        MesajChat intrebare = conversatieService.salveazaIntrebare(null, user.id(), cursId, request.intrebare());
        AkyChatResponseDto ragResponse = conversatieService.obtineRaspunsRag(
                intrebare.getConversatie().getId(), user.id(), request.intrebare());
        MesajChat raspuns = conversatieService.salveazaRaspuns(intrebare.getId(), ragResponse);

        return new RagRaspunsResponseDto(
                intrebare.getConversatie().getId(),
                new MesajChatDto(raspuns.getId(), raspuns.getRol(), raspuns.getContinut(), raspuns.getSurseFolosite(), raspuns.getCreatedAt(), raspuns.isAreRaspuns())
        );
    }

    @GetMapping("/conversatii/{id}/mesaje")
    @PreAuthorize("hasAnyRole('STUDENT', 'PROFESOR')")
    public IstoricMesajeDto getIstoric(
            @PathVariable Long id,
            @RequestParam(required = false) Long inainteDe,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser CurrentUserDto user) {
        return conversatieService.obtineIstoric(user.id(), id, inainteDe, limit);
    }

    @PostMapping("/conversatii/{id}/mesaje")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('STUDENT', 'PROFESOR')")
    public MesajChatDto adaugaMesaj(
            @PathVariable Long id,
            @Valid @RequestBody NouaIntrebareRequestDto request,
            @CurrentUser CurrentUserDto user) {
        MesajChat intrebare = conversatieService.salveazaIntrebare(id, user.id(), null, request.intrebare());
        AkyChatResponseDto ragResponse = conversatieService.obtineRaspunsRag(
                id, user.id(), request.intrebare());
        MesajChat raspuns = conversatieService.salveazaRaspuns(intrebare.getId(), ragResponse);

        return new MesajChatDto(raspuns.getId(), raspuns.getRol(), raspuns.getContinut(), raspuns.getSurseFolosite(), raspuns.getCreatedAt(), raspuns.isAreRaspuns());
    }

    @PostMapping("/conversatii/mesaje/{mesajId}/retry")
    @PreAuthorize("hasAnyRole('STUDENT', 'PROFESOR')")
    public MesajChatDto retryMesaj(
            @PathVariable Long mesajId,
            @CurrentUser CurrentUserDto user) {
        AkyChatResponseDto ragResponse = conversatieService.retryMesaj(mesajId, user.id());
        MesajChat raspuns = conversatieService.salveazaRaspuns(mesajId, ragResponse);

        return new MesajChatDto(raspuns.getId(), raspuns.getRol(), raspuns.getContinut(), raspuns.getSurseFolosite(), raspuns.getCreatedAt(), raspuns.isAreRaspuns());
    }

    @DeleteMapping("/conversatii/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('STUDENT', 'PROFESOR')")
    public void stergeConversatie(@PathVariable Long id, @CurrentUser CurrentUserDto user) {
        conversatieService.stergeConversatie(user.id(), id);
    }
}
