package com.example.akadion.controller;

import com.example.akadion.entity.Document;
import com.example.akadion.entity.User;
import com.example.akadion.exception.UserNotFoundException;
import com.example.akadion.repository.UserRepository;
import com.example.akadion.service.DocumentService;
import com.example.akadion.service.MinioStorageService;
import com.example.akadion.service.StudentCursService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/documente")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'PROFESOR', 'ADMIN')")
public class DocumentAccessController {

    private final DocumentService documentService;
    private final StudentCursService studentCursService;
    private final MinioStorageService minioStorageService;
    private final UserRepository userRepository;

    @GetMapping("/{id}/preview/{filename:.+}")
    public ResponseEntity<StreamingResponseBody> previewDocument(
            @PathVariable Long id,
            @PathVariable String filename,
            @AuthenticationPrincipal OidcUser oidcUser) {
        return buildFileResponse(resolveAccessibleDocument(id, oidcUser), true);
    }

    @GetMapping("/{id}/download/{filename:.+}")
    public ResponseEntity<StreamingResponseBody> downloadDocument(
            @PathVariable Long id,
            @PathVariable String filename,
            @AuthenticationPrincipal OidcUser oidcUser) {
        return buildFileResponse(resolveAccessibleDocument(id, oidcUser), false);
    }

    private Document resolveAccessibleDocument(Long documentId, OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        String role = user.getRol() != null ? user.getRol().getDenumire() : "";

        if ("STUDENT".equals(role)) {
            return studentCursService.getAccessibleDocument(documentId, user.getId());
        }

        return documentService.getAccessibleDocument(documentId, user.getId(), role);
    }

    private ResponseEntity<StreamingResponseBody> buildFileResponse(Document document, boolean inline) {
        MinioStorageService.StoredFile storedFile = minioStorageService.getFile(document.getPathMinio());
        ContentDisposition disposition = inline
                ? ContentDisposition.inline().filename(storedFile.filename(), StandardCharsets.UTF_8).build()
                : ContentDisposition.attachment().filename(storedFile.filename(), StandardCharsets.UTF_8).build();
        StreamingResponseBody body = outputStream -> {
            try (InputStream inputStream = storedFile.stream()) {
                inputStream.transferTo(outputStream);
            }
        };

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(parseMediaType(storedFile.contentType()));

        if (storedFile.contentLength() >= 0) {
            response.contentLength(storedFile.contentLength());
        }

        return response.body(body);
    }

    private MediaType parseMediaType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private User getLoggedUser(OidcUser oidcUser) {
        return userRepository.findByIdKeycloak(oidcUser.getSubject())
                .orElseThrow(() -> new UserNotFoundException("Utilizatorul autentificat nu are cont local."));
    }
}
