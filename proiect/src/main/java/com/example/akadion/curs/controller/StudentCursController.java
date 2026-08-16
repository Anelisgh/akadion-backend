package com.example.akadion.curs.controller;

import com.example.akadion.common.dto.AkySursaDocumentDto;
import com.example.akadion.curs.dto.CursDisponibilResponseDto;
import com.example.akadion.curs.dto.CursInrolatResponseDto;
import com.example.akadion.curs.dto.DocumentStudentResponseDto;
import com.example.akadion.curs.dto.ProfesorDetaliiResponseDto;
import com.example.akadion.curs.dto.SaptamanaStudentResponseDto;
import com.example.akadion.curs.service.StudentCursService;
import com.example.akadion.auth.security.CurrentUser;
import com.example.akadion.auth.security.CurrentUserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentCursController {

    private final StudentCursService studentCursService;

    @PostMapping("/cursuri/{cursId}/inscriere")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('STUDENT')")
    public void inscriereCurs(@PathVariable Long cursId, @CurrentUser CurrentUserDto user) {
        studentCursService.inscriereCurs(user.id(), cursId);
    }

    @PostMapping("/cursuri/{cursId}/retragere")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('STUDENT')")
    public void retragereCurs(@PathVariable Long cursId, @CurrentUser CurrentUserDto user) {
        studentCursService.retragereCurs(user.id(), cursId);
    }

    @GetMapping("/cursuri/disponibile")
    @PreAuthorize("hasRole('STUDENT')")
    public List<CursDisponibilResponseDto> listaCursuriDisponibile(@CurrentUser CurrentUserDto user) {
        return studentCursService.listaCursuriDisponibile(user.id());
    }

    @GetMapping("/cursuri/mele")
    @PreAuthorize("hasRole('STUDENT')")
    public List<CursInrolatResponseDto> listaCursuriInrolate(@CurrentUser CurrentUserDto user) {
        return studentCursService.listaCursuriInrolate(user.id());
    }

    @GetMapping("/cursuri/{cursId}/saptamani")
    @PreAuthorize("hasRole('STUDENT')")
    public List<SaptamanaStudentResponseDto> listaSaptamaniCurs(@PathVariable Long cursId, @CurrentUser CurrentUserDto user) {
        return studentCursService.listaSaptamaniCurs(user.id(), cursId);
    }

    @PostMapping("/saptamani/{saptamanaId}/complete")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('STUDENT')")
    public void bifeazaSaptamana(@PathVariable Long saptamanaId, @CurrentUser CurrentUserDto user) {
        studentCursService.bifeazaSaptamana(user.id(), saptamanaId);
    }

    @DeleteMapping("/saptamani/{saptamanaId}/complete")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('STUDENT')")
    public void debifeazaSaptamana(@PathVariable Long saptamanaId, @CurrentUser CurrentUserDto user) {
        studentCursService.debifeazaSaptamana(user.id(), saptamanaId);
    }

    @GetMapping("/saptamani/{saptamanaId}/documente")
    @PreAuthorize("hasRole('STUDENT')")
    public List<DocumentStudentResponseDto> listaDocumenteSaptamana(@PathVariable Long saptamanaId, @CurrentUser CurrentUserDto user) {
        return studentCursService.listaDocumenteSaptamana(user.id(), saptamanaId);
    }

    @GetMapping("/cursuri/{cursId}/profesor")
    @PreAuthorize("hasRole('STUDENT')")
    public ProfesorDetaliiResponseDto detaliiProfesorCurs(@PathVariable Long cursId, @CurrentUser CurrentUserDto user) {
        return studentCursService.detaliiProfesorCurs(user.id(), cursId);
    }

    @GetMapping("/cursuri/{cursId}/documente-accesibile")
    @PreAuthorize("hasRole('STUDENT')")
    public List<AkySursaDocumentDto> listaDocumenteAccesibile(
            @PathVariable Long cursId,
            @CurrentUser CurrentUserDto user) {
        return studentCursService.listaDocumenteAccesibile(user.id(), cursId);
    }
}
