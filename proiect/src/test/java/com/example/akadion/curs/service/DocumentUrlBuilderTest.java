package com.example.akadion.curs.service;

import com.example.akadion.curs.entity.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentUrlBuilderTest {

    @Mock
    private MinioStorageService minioStorageService;

    private DocumentUrlBuilder documentUrlBuilder;

    @BeforeEach
    void setUp() {
        documentUrlBuilder = new DocumentUrlBuilder(minioStorageService);
    }

    @Test
    void previewUrlUsesDocumentIdAndEncodedOriginalFilename() {
        Document document = new Document();
        document.setId(42L);
        document.setPathMinio("curs-1/saptamana-2/uuid-curs de java.pdf");

        when(minioStorageService.extractOriginalFilename(document.getPathMinio())).thenReturn("curs de java.pdf");

        String url = documentUrlBuilder.previewUrl(document);

        assertThat(url).isEqualTo("/api/documente/42/preview/curs%20de%20java.pdf");
    }

    @Test
    void downloadUrlUsesDocumentIdAndEncodedOriginalFilename() {
        Document document = new Document();
        document.setId(42L);
        document.setPathMinio("curs-1/saptamana-2/uuid-curs.pdf");

        when(minioStorageService.extractOriginalFilename(document.getPathMinio())).thenReturn("curs.pdf");

        String url = documentUrlBuilder.downloadUrl(document);

        assertThat(url).isEqualTo("/api/documente/42/download/curs.pdf");
    }
}
