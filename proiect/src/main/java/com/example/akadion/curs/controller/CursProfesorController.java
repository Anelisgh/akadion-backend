package com.example.akadion.curs.controller;

import com.example.akadion.akychat.dto.AkyChatRequestDto;
import com.example.akadion.akychat.dto.AkyChatResponseDto;
import com.example.akadion.curs.dto.CursRequestDto;
import com.example.akadion.curs.dto.CursResponseDto;
import com.example.akadion.curs.dto.StudentCursDto;
import com.example.akadion.common.entity.NumeRol;
import com.example.akadion.auth.security.CurrentUser;
import com.example.akadion.auth.security.CurrentUserDto;
import com.example.akadion.curs.service.CursService;
import com.example.akadion.akychat.service.RagChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profesor/cursuri")
@RequiredArgsConstructor
public class CursProfesorController {

    private final CursService cursService;
    private final RagChatService ragChatService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PROFESOR', 'ADMIN')")
    public List<CursResponseDto> listaCursuri(@CurrentUser CurrentUserDto user) {
        String role = user.rol();
        if (NumeRol.ADMIN.name().equals(role)) {
            return cursService.listaToateCursurile();
        } else {
            return cursService.listaCursuriProprii(user.id());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROFESOR', 'ADMIN')")
    public CursResponseDto getCursById(@PathVariable Long id, @CurrentUser CurrentUserDto user) {
        String role = user.rol();
        return cursService.getCursById(id, user.id(), role);
    }

    @GetMapping("/{id}/studenti")
    @PreAuthorize("hasAnyRole('PROFESOR', 'ADMIN')")
    public List<StudentCursDto> listaStudentiActivi(@PathVariable Long id, @CurrentUser CurrentUserDto user) {
        String role = user.rol();
        return cursService.listaStudentiActivi(id, user.id(), role);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PROFESOR')")
    public CursResponseDto creazaCurs(@Valid @RequestBody CursRequestDto dto, @CurrentUser CurrentUserDto user) {
        return cursService.creazaCurs(user.id(), dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PROFESOR')")
    public CursResponseDto modificaCurs(@PathVariable Long id, @Valid @RequestBody CursRequestDto dto, @CurrentUser CurrentUserDto user) {
        return cursService.modificaCurs(id, user.id(), dto);
    }

    @PatchMapping("/{id}/dezactiveaza")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('PROFESOR')")
    public void dezactiveazaCurs(@PathVariable Long id, @CurrentUser CurrentUserDto user) {
        cursService.dezactiveazaCurs(id, user.id());
    }

    @PatchMapping("/{id}/activeaza")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('PROFESOR')")
    public void activeazaCurs(@PathVariable Long id, @CurrentUser CurrentUserDto user) {
        cursService.activeazaCurs(id, user.id());
    }

    @PostMapping("/{cursId}/chat")
    @PreAuthorize("hasRole('PROFESOR')")
    public AkyChatResponseDto chatAkyProfesor(
            @PathVariable Long cursId,
            @Valid @RequestBody AkyChatRequestDto request,
            @CurrentUser CurrentUserDto user) {

        // getCursById aruncă excepție dacă profesorul nu deține cursul — verificăm doar accesul, ignorăm rezultatul.
        cursService.getCursById(cursId, user.id(), NumeRol.PROFESOR.name());

        return ragChatService.intreabaAky(user.id(), cursId, request);
    }
}
