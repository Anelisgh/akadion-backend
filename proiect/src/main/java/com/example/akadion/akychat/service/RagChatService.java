package com.example.akadion.akychat.service;

import com.example.akadion.akychat.dto.AkyChatRequestDto;
import com.example.akadion.akychat.dto.AkyChatResponseDto;
import com.example.akadion.common.dto.AkySursaDocumentDto;
import com.example.akadion.exception.RagChatException;
import com.example.akadion.curs.repository.DocumentRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
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
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatService {

    private static final String CURS_ID_KEY = "cursId";
    private static final String DOCUMENT_ID_KEY = "documentId";

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

    // Folosit doar pentru caller-ii care nu au (încă) un progres real de student calculabil
    // (ex. chat-ul din ConversatieService, accesibil și profesorilor) — vezi intreabaAky(4 args).
    private static final int DEFAULT_MAX_SAPTAMANA_PROFESOR = 100;

    public AkyChatResponseDto intreabaAky(Long userId, Long cursId, AkyChatRequestDto request) {
        return intreabaAky(userId, cursId, request, DEFAULT_MAX_SAPTAMANA_PROFESOR);
    }

    public AkyChatResponseDto intreabaAky(Long userId, Long cursId, AkyChatRequestDto request, int maxSaptamanaParcursa) {
        return executaApelRag(() -> {
            Map<String, Object> ragPayload = buildChatPayload(userId, cursId, request, maxSaptamanaParcursa);

            log.info("Trimitere cerere RAG Chat pentru utilizatorul {} la cursul {}.", userId, cursId);

            Map<String, Object> responseMap = restClient.post()
                    .uri("/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ragPayload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (responseMap == null) {
                throw new RagChatException("Serviciul RAG a returnat un răspuns vid.");
            }

            Object rawRaspuns = responseMap.get("raspuns");
            String raspunsText = rawRaspuns != null ? rawRaspuns.toString() : "";
            List<AkySursaDocumentDto> surseDtos = parseSurseFolosite(responseMap.get("surseFolosite"));

            return new AkyChatResponseDto(raspunsText, surseDtos);
        }, "chat cursId=" + cursId);
    }

    // Map request for Python RAG contract (contract-rag.md)
    private Map<String, Object> buildChatPayload(Long userId, Long cursId, AkyChatRequestDto request, int maxSaptamanaParcursa) {
        List<Map<String, String>> istoricMapped = request.istoricConversatie().stream()
                .map(msg -> Map.of(
                        "role", "user".equalsIgnoreCase(msg.sender()) ? "user" : "assistant",
                        "content", msg.text()
                ))
                .toList();

        return Map.of(
                "studentId", userId,
                CURS_ID_KEY, cursId,
                "maxSaptamanaParcursa", maxSaptamanaParcursa,
                "intrebare", request.intrebare(),
                "istoricConversatie", istoricMapped
        );
    }

    private List<AkySursaDocumentDto> parseSurseFolosite(Object rawSurse) {
        List<AkySursaDocumentDto> surseDtos = new ArrayList<>();
        if (!(rawSurse instanceof List<?> list)) {
            return surseDtos;
        }

        for (Object item : list) {
            if (item instanceof Number numDocId) {
                Long docId = numDocId.longValue();
                documentRepository.findById(docId)
                        .ifPresent(doc -> surseDtos.add(new AkySursaDocumentDto(doc.getId(), doc.getTitlu())));
            } else if (item instanceof Map<?, ?> mapDoc) {
                Object rawId = mapDoc.get(DOCUMENT_ID_KEY);
                Object rawNume = mapDoc.get("numeFisier");
                String numeFisier = rawNume != null ? rawNume.toString() : "Document";
                Long docId = rawId instanceof Number n ? n.longValue() : null;
                surseDtos.add(new AkySursaDocumentDto(docId, numeFisier));
            }
        }
        return surseDtos;
    }

    public List<Map<String, Object>> genereazaQuiz(Long cursId, Integer maxSaptamana, Long documentId, Integer nrIntrebari, String dificultate) {
        return executaApelRag(() -> {
            Map<String, Object> ragPayload = new HashMap<>();
            ragPayload.put(CURS_ID_KEY, cursId);
            if (maxSaptamana != null) {
                ragPayload.put("maxSaptamana", maxSaptamana);
            }
            if (documentId != null) {
                ragPayload.put(DOCUMENT_ID_KEY, documentId);
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
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (response == null) {
                throw new RagChatException("Serviciul RAG a returnat un răspuns vid.");
            }

            return response;
        }, "quiz cursId=" + cursId);
    }

    public List<Map<String, Object>> genereazaFlashcards(Long cursId, Integer maxSaptamana, Long documentId, Integer nrFlashcards) {
        return executaApelRag(() -> {
            Map<String, Object> ragPayload = new HashMap<>();
            ragPayload.put(CURS_ID_KEY, cursId);
            if (maxSaptamana != null) {
                ragPayload.put("maxSaptamana", maxSaptamana);
            }
            if (documentId != null) {
                ragPayload.put(DOCUMENT_ID_KEY, documentId);
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
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (response == null) {
                throw new RagChatException("Serviciul RAG a returnat un răspuns vid.");
            }

            return response;
        }, "flashcards cursId=" + cursId);
    }

    private <T> T executaApelRag(Supplier<T> apel, String context) {
        try {
            return apel.get();
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Autentificare eșuată către serviciul RAG — verifică dacă secretul e sincronizat cu echipa RAG ({})", context);
            throw new RagChatException("Serviciul Aky este temporar indisponibil. Încearcă din nou în câteva momente.", e);
        } catch (RagChatException e) {
            throw e;
        } catch (Exception e) {
            log.error("Eroare la comunicarea cu RAG ({}): {}", context, e.getMessage(), e);
            throw new RagChatException("Serviciul Aky este temporar indisponibil. Încearcă din nou în câteva momente.", e);
        }
    }
}
