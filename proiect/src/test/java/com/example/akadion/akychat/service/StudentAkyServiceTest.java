package com.example.akadion.akychat.service;

import com.example.akadion.akychat.dto.AkyChatRequestDto;
import com.example.akadion.akychat.dto.AkyChatResponseDto;
import com.example.akadion.akychat.dto.FlashcardGenerateRequestDto;
import com.example.akadion.curs.entity.Curs;
import com.example.akadion.curs.entity.Document;
import com.example.akadion.curs.entity.Saptamana;
import com.example.akadion.exception.ForbiddenOperationException;
import com.example.akadion.curs.repository.DocumentRepository;
import com.example.akadion.curs.service.StudentCursService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentAkyServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private RagChatService ragChatService;
    @Mock
    private StudentCursService studentCursService;

    private StudentAkyService studentAkyService;

    @BeforeEach
    void setUp() {
        studentAkyService = new StudentAkyService(documentRepository, ragChatService, studentCursService);
    }

    @Test
    void intreabaAkyChecksRateLimitAndForwardsRealMaxSaptamana() {
        when(studentCursService.determinaSaptamanaParcursaMax(1L, 10L)).thenReturn(4);
        AkyChatRequestDto request = new AkyChatRequestDto("Ce e un JOIN?", List.of());
        AkyChatResponseDto expected = new AkyChatResponseDto("Un JOIN combină rânduri...", List.of());
        when(ragChatService.intreabaAky(1L, 10L, request, 4)).thenReturn(expected);

        AkyChatResponseDto result = studentAkyService.intreabaAky(1L, 10L, request);

        verify(studentCursService).verificaRateLimitAky(1L);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void genereazaFlashcardsUsesDefaultCountWhenRequestIsNull() {
        when(studentCursService.determinaSaptamanaParcursaMax(1L, 10L)).thenReturn(3);
        when(ragChatService.genereazaFlashcards(10L, 3, null, 5)).thenReturn(List.of(Map.of("intrebare", "x")));

        List<Map<String, Object>> result = studentAkyService.genereazaFlashcards(1L, 10L, null);

        verify(studentCursService).verificaRateLimitAky(1L);
        assertThat(result).hasSize(1);
    }

    @Test
    void genereazaFlashcardsPassesThroughDocumentIdWhenDocumentIsAccessible() {
        when(studentCursService.determinaSaptamanaParcursaMax(1L, 10L)).thenReturn(3);
        Document document = accessibleDocument(10L, 2);
        when(documentRepository.findWithSaptamanaAndCursAndProfesorById(99L)).thenReturn(Optional.of(document));
        when(ragChatService.genereazaFlashcards(10L, 3, 99L, 5)).thenReturn(List.of());

        studentAkyService.genereazaFlashcards(1L, 10L, new FlashcardGenerateRequestDto(99L, null));

        verify(ragChatService).genereazaFlashcards(10L, 3, 99L, 5);
    }

    @Test
    void genereazaFlashcardsThrowsWhenDocumentIsNotActive() {
        when(studentCursService.determinaSaptamanaParcursaMax(1L, 10L)).thenReturn(3);
        Document document = accessibleDocument(10L, 1);
        document.setActiv(false);
        when(documentRepository.findWithSaptamanaAndCursAndProfesorById(99L)).thenReturn(Optional.of(document));

        FlashcardGenerateRequestDto requestDto = new FlashcardGenerateRequestDto(99L, null);
        assertThatThrownBy(() -> studentAkyService.genereazaFlashcards(1L, 10L, requestDto))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("nu este activ");
    }

    @Test
    void genereazaFlashcardsThrowsWhenDocumentBelongsToAnotherCourse() {
        when(studentCursService.determinaSaptamanaParcursaMax(1L, 10L)).thenReturn(3);
        Document document = accessibleDocument(999L, 1);
        when(documentRepository.findWithSaptamanaAndCursAndProfesorById(99L)).thenReturn(Optional.of(document));

        FlashcardGenerateRequestDto requestDto = new FlashcardGenerateRequestDto(99L, null);
        assertThatThrownBy(() -> studentAkyService.genereazaFlashcards(1L, 10L, requestDto))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("nu aparține acestui curs");
    }

    @Test
    void genereazaFlashcardsThrowsWhenDocumentNotYetReachedInProgress() {
        when(studentCursService.determinaSaptamanaParcursaMax(1L, 10L)).thenReturn(1);
        Document document = accessibleDocument(10L, 5);
        when(documentRepository.findWithSaptamanaAndCursAndProfesorById(99L)).thenReturn(Optional.of(document));

        FlashcardGenerateRequestDto requestDto = new FlashcardGenerateRequestDto(99L, null);
        assertThatThrownBy(() -> studentAkyService.genereazaFlashcards(1L, 10L, requestDto))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("nu este accesibil");
    }

    @Test
    void genereazaFlashcardsThrowsForInvalidDocumentId() {
        when(studentCursService.determinaSaptamanaParcursaMax(1L, 10L)).thenReturn(3);
        when(documentRepository.findWithSaptamanaAndCursAndProfesorById(404L)).thenReturn(Optional.empty());

        FlashcardGenerateRequestDto requestDto = new FlashcardGenerateRequestDto(404L, null);
        assertThatThrownBy(() -> studentAkyService.genereazaFlashcards(1L, 10L, requestDto))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Document accessibleDocument(Long cursId, int nrSaptamana) {
        Curs curs = new Curs();
        curs.setId(cursId);

        Saptamana saptamana = new Saptamana();
        saptamana.setNrSaptamana(nrSaptamana);
        saptamana.setCurs(curs);

        Document document = new Document();
        document.setId(99L);
        document.setActiv(true);
        document.setSaptamana(saptamana);
        return document;
    }
}
