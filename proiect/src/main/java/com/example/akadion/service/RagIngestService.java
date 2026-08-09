package com.example.akadion.service;

import com.example.akadion.entity.Curs;
import com.example.akadion.entity.Document;
import com.example.akadion.entity.Saptamana;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagIngestService {

    @Qualifier("ragEmbedderRestClient")
    private final RestClient ragEmbedderRestClient;

    private RestClient restClient;

    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout((int) Duration.ofSeconds(120).toMillis());
        
        this.restClient = ragEmbedderRestClient.mutate().requestFactory(factory).build();
    }

    public boolean trimiteLaIngest(Document document, Saptamana saptamana, Curs curs) {
        try {
            String path = document.getPathMinio();
            String extensie = (path != null && path.contains(".")) 
                    ? path.substring(path.lastIndexOf(".") + 1).toLowerCase() 
                    : "";

            Map<String, Object> response = restClient.post()
                    .uri("/api/documents/ingest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "document_id", document.getId(),
                            "course_id", curs.getId(),
                            "week_id", saptamana.getNrSaptamana(),
                            "professor_id", curs.getProfesor().getId(),
                            "document_title", document.getTitlu(),
                            "path_minio", path != null ? path : ""
                    ))
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
                    
            if (response != null && "FAILED".equals(response.get("status"))) {
                log.error("Serviciul RAG a returnat status FAILED la ingestie pentru documentul {}", document.getId());
                return false;
            }
            return true;
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Autentificare eșuată către serviciul RAG — verifică dacă secretul e sincronizat cu echipa RAG (documentId={})", document.getId());
            return false;
        } catch (Exception e) {
            log.error("Eroare la trimiterea documentului {} către RAG: {}", document.getId(), e.getMessage());
            return false;
        }
    }

    public void stergeDinIngest(Long documentId) {
        try {
            restClient.delete()
                    .uri("/api/documents/ingest/" + documentId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Autentificare eșuată către serviciul RAG — verifică dacă secretul e sincronizat cu echipa RAG (documentId={})", documentId);
        } catch (Exception e) {
            log.error("Eroare la ștergerea documentului {} din RAG: {}", documentId, e.getMessage());
        }
    }
}
