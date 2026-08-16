package com.example.akadion.curs.controller;

import com.example.akadion.curs.dto.SaptamanaRequestDto;
import com.example.akadion.curs.dto.SaptamanaResponseDto;
import com.example.akadion.auth.security.CurrentUser;
import com.example.akadion.auth.security.CurrentUserDto;
import com.example.akadion.curs.service.SaptamanaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profesor")
@RequiredArgsConstructor
public class SaptamanaProfesorController {

    private final SaptamanaService saptamanaService;

    @GetMapping("/cursuri/{cursId}/saptamani")
    @PreAuthorize("hasAnyRole('PROFESOR', 'ADMIN')")
    public List<SaptamanaResponseDto> listaSaptamani(
            @PathVariable Long cursId,
            @CurrentUser CurrentUserDto user) {
        String role = user.rol();
        return saptamanaService.listaSaptamani(cursId, user.id(), role);
    }

    @PostMapping("/cursuri/{cursId}/saptamani")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PROFESOR')")
    public SaptamanaResponseDto adaugaSaptamana(
            @PathVariable Long cursId,
            @Valid @RequestBody SaptamanaRequestDto dto,
            @CurrentUser CurrentUserDto user) {
        return saptamanaService.adaugaSaptamana(cursId, user.id(), dto);
    }

    @PutMapping("/saptamani/{id}")
    @PreAuthorize("hasRole('PROFESOR')")
    public SaptamanaResponseDto modificaSaptamana(
            @PathVariable Long id,
            @Valid @RequestBody SaptamanaRequestDto dto,
            @CurrentUser CurrentUserDto user) {
        return saptamanaService.modificaSaptamana(id, user.id(), dto);
    }

    @DeleteMapping("/saptamani/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PROFESOR')")
    public void stergeUltimaSaptamana(
            @PathVariable Long id,
            @CurrentUser CurrentUserDto user) {
        saptamanaService.stergeUltimaSaptamana(id, user.id());
    }
}
