package com.example.akadion.curs.service;

import com.example.akadion.curs.entity.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

// Construiește URL-urile de preview/download pentru documente. Extras din DocumentService și
// StudentCursService, unde logica era duplicată identic.
@Component
@RequiredArgsConstructor
public class DocumentUrlBuilder {

    private final MinioStorageService minioStorageService;

    public String previewUrl(Document document) {
        return "/api/documente/%d/preview/%s".formatted(document.getId(), encodedFilename(document));
    }

    public String downloadUrl(Document document) {
        return "/api/documente/%d/download/%s".formatted(document.getId(), encodedFilename(document));
    }

    private String encodedFilename(Document document) {
        return UriUtils.encodePathSegment(minioStorageService.extractOriginalFilename(document.getPathMinio()), StandardCharsets.UTF_8);
    }
}
