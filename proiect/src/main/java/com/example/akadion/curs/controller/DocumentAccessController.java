package com.example.akadion.curs.controller;

import com.example.akadion.curs.entity.Document;
import com.example.akadion.common.entity.NumeRol;
import com.example.akadion.auth.security.CurrentUser;
import com.example.akadion.auth.security.CurrentUserDto;
import com.example.akadion.curs.service.DocumentService;
import com.example.akadion.curs.service.MinioStorageService;
import com.example.akadion.curs.service.StudentCursService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api/documente")
@RequiredArgsConstructor
public class DocumentAccessController {

    private final DocumentService documentService;
    private final StudentCursService studentCursService;
    private final MinioStorageService minioStorageService;

    @GetMapping("/{id}/preview/{filename:.+}")
    @PreAuthorize("hasAnyRole('STUDENT', 'PROFESOR', 'ADMIN')")
    public ResponseEntity<StreamingResponseBody> previewDocument(
            @PathVariable Long id,
            @PathVariable String filename,
            @CurrentUser CurrentUserDto user) {
        return buildFileResponse(resolveAccessibleDocument(id, user), true);
    }

    @GetMapping("/{id}/download/{filename:.+}")
    @PreAuthorize("hasAnyRole('STUDENT', 'PROFESOR', 'ADMIN')")
    public ResponseEntity<StreamingResponseBody> downloadDocument(
            @PathVariable Long id,
            @PathVariable String filename,
            @CurrentUser CurrentUserDto user) {
        return buildFileResponse(resolveAccessibleDocument(id, user), false);
    }

    private Document resolveAccessibleDocument(Long documentId, CurrentUserDto user) {
        String role = user.rol();

        if (NumeRol.STUDENT.name().equals(role)) {
            return studentCursService.getAccessibleDocument(documentId, user.id());
        }

        return documentService.getAccessibleDocument(documentId, user.id(), role);
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
        } catch (IllegalArgumentException ex) {
            log.debug("Tip MIME invalid din storage ('{}'), se folosește fallback application/octet-stream.", contentType, ex);
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
