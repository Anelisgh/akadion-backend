package com.example.akadion.akychat.service;

import com.example.akadion.akychat.dto.AkyChatRequestDto;
import com.example.akadion.akychat.dto.AkyChatResponseDto;
import com.example.akadion.akychat.dto.FlashcardGenerateRequestDto;
import com.example.akadion.curs.entity.Document;
import com.example.akadion.curs.entity.Saptamana;
import com.example.akadion.exception.ForbiddenOperationException;
import com.example.akadion.curs.repository.DocumentRepository;
import com.example.akadion.curs.service.StudentCursService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentAkyService {

    private final DocumentRepository documentRepository;
    private final RagChatService ragChatService;
    private final StudentCursService studentCursService;

    public AkyChatResponseDto intreabaAky(Long studentId, Long cursId, AkyChatRequestDto request) {
        studentCursService.verificaRateLimitAky(studentId);
        int maxSaptamana = studentCursService.determinaSaptamanaParcursaMax(studentId, cursId);
        return ragChatService.intreabaAky(studentId, cursId, request, maxSaptamana);
    }

    public List<Map<String, Object>> genereazaFlashcards(Long studentId, Long cursId, FlashcardGenerateRequestDto request) {
        studentCursService.verificaRateLimitAky(studentId);
        int maxSaptamana = studentCursService.determinaSaptamanaParcursaMax(studentId, cursId);
        Long documentId = request != null ? request.documentId() : null;
        Integer nrFlashcards = request != null && request.nrFlashcards() != null ? request.nrFlashcards() : 5;

        if (documentId != null) {
            Document document = documentRepository.findWithSaptamanaAndCursAndProfesorById(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Documentul nu a fost găsit."));

            if (!document.isActiv()) {
                throw new ForbiddenOperationException("Documentul nu este activ.");
            }

            Saptamana saptamana = document.getSaptamana();
            if (saptamana == null || saptamana.getCurs() == null || !saptamana.getCurs().getId().equals(cursId)) {
                throw new ForbiddenOperationException("Documentul nu aparține acestui curs.");
            }

            Integer nrSaptamana = saptamana.getNrSaptamana();
            if (nrSaptamana == null || nrSaptamana > maxSaptamana) {
                throw new ForbiddenOperationException("Documentul nu este accesibil încă.");
            }
        }

        return ragChatService.genereazaFlashcards(cursId, maxSaptamana, documentId, nrFlashcards);
    }
}
