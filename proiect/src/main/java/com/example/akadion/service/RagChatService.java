package com.example.akadion.service;

import com.example.akadion.dto.AkyChatRequestDto;
import com.example.akadion.dto.AkyChatResponseDto;
import com.example.akadion.dto.AkySursaDocumentDto;
import com.example.akadion.exception.RagChatException;
import com.example.akadion.repository.DocumentRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatService {

    @Qualifier("ragChatRestClient")
    private final RestClient ragChatRestClient;
    private final DocumentRepository documentRepository;

    private RestClient restClient;

    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());

        this.restClient = ragChatRestClient.mutate()
                .requestFactory(requestFactory)
                .build();
    }

    public AkyChatResponseDto intreabaAky(Long userId, Long cursId, AkyChatRequestDto request) {
        try {
            // Map request for Python RAG contract (contract-rag.md)
            List<Map<String, String>> istoricMapped = request.istoricConversatie().stream()
                    .map(msg -> Map.of(
                            "role", "user".equalsIgnoreCase(msg.sender()) ? "user" : "assistant",
                            "content", msg.text()
                    ))
                    .toList();

            Map<String, Object> ragPayload = Map.of(
                    "studentId", userId,
                    "cursId", cursId,
                    "maxSaptamanaParcursa", 100, // Hardcodat la 100 până implementăm progresul real
                    "intrebare", request.intrebare(),
                    "istoricConversatie", istoricMapped
            );

            log.info("Trimitere cerere RAG Chat pentru utilizatorul {} la cursul {}.", userId, cursId);

            Map<String, Object> responseMap = restClient.post()
                    .uri("/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ragPayload)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

            if (responseMap == null) {
                throw new RagChatException("Serviciul RAG a returnat un răspuns vid.");
            }

            Object rawRaspuns = responseMap.get("raspuns");
            String raspunsText = rawRaspuns != null ? rawRaspuns.toString() : "";
            List<AkySursaDocumentDto> surseDtos = new ArrayList<>();

            Object rawSurse = responseMap.get("surseFolosite");
            if (rawSurse instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Number numDocId) {
                        Long docId = numDocId.longValue();
                        documentRepository.findById(docId).ifPresent(doc -> {
                            surseDtos.add(new AkySursaDocumentDto(doc.getId(), doc.getTitlu()));
                        });
                    } else if (item instanceof Map<?, ?> mapDoc) {
                        Object rawId = mapDoc.get("documentId");
                        Object rawNume = mapDoc.get("numeFisier");
                        String numeFisier = rawNume != null ? rawNume.toString() : "Document";
                        Long docId = rawId instanceof Number n ? n.longValue() : null;
                        surseDtos.add(new AkySursaDocumentDto(docId, numeFisier));
                    }
                }
            }

            return new AkyChatResponseDto(raspunsText, surseDtos);

        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Autentificare eșuată către serviciul RAG — verifică dacă secretul e sincronizat cu echipa RAG (cursId={})", cursId);
            throw new RagChatException("Serviciul Aky este temporar indisponibil. Încearcă din nou în câteva momente.", e);
        } catch (RagChatException e) {
            throw e;
        } catch (Exception e) {
            log.error("Eroare la comunicarea cu RAG Chat pentru cursul {}: {}", cursId, e.getMessage(), e);
            throw new RagChatException("Serviciul Aky este temporar indisponibil. Încearcă din nou în câteva momente.", e);
        }
    }

    public List<Map<String, Object>> genereazaQuiz(Long cursId, Integer maxSaptamana, Long documentId, Integer nrIntrebari, String dificultate) {
        try {
            Map<String, Object> ragPayload = new HashMap<>();
            ragPayload.put("cursId", cursId);
            if (maxSaptamana != null) {
                ragPayload.put("maxSaptamana", maxSaptamana);
            }
            if (documentId != null) {
                ragPayload.put("documentId", documentId);
            }
            if (nrIntrebari != null) {
                ragPayload.put("nrIntrebari", nrIntrebari);
            }
            if (dificultate != null) {
                ragPayload.put("dificultate", dificultate);
            }

            log.info("Trimitere cerere RAG Quiz pentru cursul {} (maxSaptamana={}, documentId={}, nrIntrebari={}, dificultate={}).", cursId, maxSaptamana, documentId, nrIntrebari, dificultate);

            List<Map<String, Object>> response = restClient.post()
                    .uri("/quiz/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ragPayload)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (response == null) {
                throw new RagChatException("Serviciul RAG a returnat un răspuns vid.");
            }

            return response;
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Autentificare eșuată către serviciul RAG la generare quiz (cursId={})", cursId);
            throw new RagChatException("Serviciul Aky este temporar indisponibil. Încearcă din nou în câteva momente.", e);
        } catch (RagChatException e) {
            throw e;
        } catch (Exception e) {
            log.error("Eroare la comunicarea cu RAG la generare quiz pentru cursul {}: {}", cursId, e.getMessage(), e);
            throw new RagChatException("Serviciul Aky este temporar indisponibil. Încearcă din nou în câteva momente.", e);
        }
    }

    public List<Map<String, Object>> genereazaFlashcards(Long cursId, Integer maxSaptamana, Long documentId, Integer nrFlashcards) {
        try {
            Map<String, Object> ragPayload = new HashMap<>();
            ragPayload.put("cursId", cursId);
            if (maxSaptamana != null) {
                ragPayload.put("maxSaptamana", maxSaptamana);
            }
            if (documentId != null) {
                ragPayload.put("documentId", documentId);
            }
            if (nrFlashcards != null) {
                ragPayload.put("nrFlashcards", nrFlashcards);
            }

            log.info("Trimitere cerere RAG Flashcards pentru cursul {} (maxSaptamana={}, documentId={}, nrFlashcards={}).", cursId, maxSaptamana, documentId, nrFlashcards);

            List<Map<String, Object>> response = restClient.post()
                    .uri("/flashcards/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ragPayload)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (response == null) {
                throw new RagChatException("Serviciul RAG a returnat un răspuns vid.");
            }

            return response;
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Autentificare eșuată către serviciul RAG la generare flashcards (cursId={})", cursId);
            throw new RagChatException("Serviciul Aky este temporar indisponibil. Încearcă din nou în câteva momente.", e);
        } catch (RagChatException e) {
            throw e;
        } catch (Exception e) {
            log.error("Eroare la comunicarea cu RAG la generare flashcards pentru cursul {}: {}", cursId, e.getMessage(), e);
            throw new RagChatException("Serviciul Aky este temporar indisponibil. Încearcă din nou în câteva momente.", e);
        }
    }
}
