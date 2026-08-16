package com.example.akadion.curs.controller;

import com.example.akadion.curs.dto.DocumentResponseDto;
import com.example.akadion.auth.security.CurrentUser;
import com.example.akadion.auth.security.CurrentUserDto;
import com.example.akadion.curs.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/profesor")
@RequiredArgsConstructor
public class DocumentProfesorController {

    private final DocumentService documentService;

    @GetMapping("/saptamani/{saptamanaId}/documente")
    @PreAuthorize("hasAnyRole('PROFESOR', 'ADMIN')")
    public List<DocumentResponseDto> listaDocumente(
            @PathVariable Long saptamanaId,
            @CurrentUser CurrentUserDto user) {
        String role = user.rol();
        return documentService.listaDocumente(saptamanaId, user.id(), role);
    }

    @PostMapping(value = "/saptamani/{saptamanaId}/documente", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PROFESOR')")
    public DocumentResponseDto adaugaDocument(
            @PathVariable Long saptamanaId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("titlu") String titlu,
            @CurrentUser CurrentUserDto user) {
        return documentService.adaugaDocument(saptamanaId, user.id(), file, titlu);
    }

    @PutMapping(value = "/documente/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PROFESOR')")
    public DocumentResponseDto modificaDocument(
            @PathVariable Long id,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "titlu", required = false) String titlu,
            @CurrentUser CurrentUserDto user) {
        return documentService.modificaDocument(id, user.id(), titlu, file);
    }

    @DeleteMapping("/documente/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PROFESOR')")
    public void stergeDocument(
            @PathVariable Long id,
            @CurrentUser CurrentUserDto user) {
        documentService.stergeDocument(id, user.id());
    }

    @PostMapping("/documente/{id}/retry-ingest")
    @PreAuthorize("hasRole('PROFESOR')")
    public DocumentResponseDto reincearcaIngest(
            @PathVariable Long id,
            @CurrentUser CurrentUserDto user) {
        return documentService.reincearcaIngest(id, user.id());
    }
}
