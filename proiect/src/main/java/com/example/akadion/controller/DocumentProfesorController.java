package com.example.akadion.controller;

import com.example.akadion.dto.DocumentResponseDto;
import com.example.akadion.entity.User;
import com.example.akadion.exception.UserNotFoundException;
import com.example.akadion.repository.UserRepository;
import com.example.akadion.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/profesor")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PROFESOR', 'ADMIN')")
public class DocumentProfesorController {

    private final DocumentService documentService;
    private final UserRepository userRepository;

    @GetMapping("/saptamani/{saptamanaId}/documente")
    public List<DocumentResponseDto> listaDocumente(
            @PathVariable Long saptamanaId,
            @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        String role = user.getRol() != null ? user.getRol().getDenumire() : "";
        return documentService.listaDocumente(saptamanaId, user.getId(), role);
    }

    @PostMapping(value = "/saptamani/{saptamanaId}/documente", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PROFESOR')")
    public DocumentResponseDto adaugaDocument(
            @PathVariable Long saptamanaId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("titlu") String titlu,
            @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        return documentService.adaugaDocument(saptamanaId, user.getId(), file, titlu);
    }

    @PutMapping(value = "/documente/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PROFESOR')")
    public DocumentResponseDto modificaDocument(
            @PathVariable Long id,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "titlu", required = false) String titlu,
            @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        return documentService.modificaDocument(id, user.getId(), titlu, file);
    }

    @DeleteMapping("/documente/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('PROFESOR')")
    public void stergeDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        documentService.stergeDocument(id, user.getId());
    }

    @PostMapping("/documente/{id}/retry-ingest")
    @PreAuthorize("hasRole('PROFESOR')")
    public DocumentResponseDto reincearcaIngest(
            @PathVariable Long id,
            @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        return documentService.reincearcaIngest(id, user.getId());
    }

    private User getLoggedUser(OidcUser oidcUser) {
        return userRepository.findByIdKeycloak(oidcUser.getSubject())
                .orElseThrow(() -> new UserNotFoundException("Utilizatorul autentificat nu are cont local."));
    }
}
