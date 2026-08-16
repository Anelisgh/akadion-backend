package com.example.akadion.quiz.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QuizGradingServiceTest {

    private final QuizGradingService quizGradingService = new QuizGradingService();

    @Test
    void normalizeQuizOptionsHandlesMapInput() {
        Map<String, Object> raw = Map.of("A", "Paris", "B", "Londra");

        Map<String, Object> normalized = quizGradingService.normalizeQuizOptions(raw);

        assertThat(normalized).containsExactlyInAnyOrderEntriesOf(raw);
    }

    @Test
    void normalizeQuizOptionsConvertsListToLetteredKeys() {
        Map<String, Object> normalized = quizGradingService.normalizeQuizOptions(List.of("Paris", "Londra", "Roma"));

        assertThat(normalized)
                .containsEntry("A", "Paris")
                .containsEntry("B", "Londra")
                .containsEntry("C", "Roma");
    }

    @Test
    void normalizeQuizOptionsReturnsEmptyMapForNullOrUnknownType() {
        assertThat(quizGradingService.normalizeQuizOptions(null)).isEmpty();
        assertThat(quizGradingService.normalizeQuizOptions("nu e nici map nici listă")).isEmpty();
    }

    @Test
    void sanitizeQuizQuestionFillsIndexAndReadsFields() {
        Map<String, Object> raw = Map.of(
                "intrebare", "Care e capitala Franței?",
                "raspuns_corect", "A",
                "explicatie", "Paris e capitala.",
                "optiuni", List.of("Paris", "Londra")
        );

        Map<String, Object> sanitized = quizGradingService.sanitizeQuizQuestion(raw, 2);

        assertThat(sanitized).containsEntry("index", 2);
        assertThat(sanitized).containsEntry("intrebare", "Care e capitala Franței?");
        assertThat(sanitized).containsEntry("raspuns_corect", "A");
        assertThat(sanitized).containsEntry("explicatie", "Paris e capitala.");
        @SuppressWarnings("unchecked")
        Map<String, Object> optiuni = (Map<String, Object>) sanitized.get("optiuni");
        assertThat(optiuni).containsEntry("A", "Paris");
    }

    @Test
    void sanitizeQuizQuestionHandlesNullInput() {
        Map<String, Object> sanitized = quizGradingService.sanitizeQuizQuestion(null, 0);

        assertThat(sanitized).containsEntry("index", 0);
        assertThat(sanitized.get("intrebare")).isNull();
    }

    @Test
    void isQuizAnswerCorrectMatchesExactValue() {
        boolean correct = quizGradingService.isQuizAnswerCorrect("A", "A", Map.of("A", "Paris", "B", "Londra"));

        assertThat(correct).isTrue();
    }

    @Test
    void isQuizAnswerCorrectIsCaseInsensitiveAndTrims() {
        boolean correct = quizGradingService.isQuizAnswerCorrect("  a  ", "A", Map.of("A", "Paris"));

        assertThat(correct).isTrue();
    }

    @Test
    void isQuizAnswerCorrectMatchesWhenCorrectAnswerIsTheOptionValueNotTheKey() {
        // raspuns_corect vine din RAG uneori ca text-ul opțiunii, nu ca litera-cheie
        boolean correct = quizGradingService.isQuizAnswerCorrect("A", "Paris", Map.of("A", "Paris", "B", "Londra"));

        assertThat(correct).isTrue();
    }

    @Test
    void isQuizAnswerCorrectReturnsFalseForWrongAnswer() {
        boolean correct = quizGradingService.isQuizAnswerCorrect("B", "A", Map.of("A", "Paris", "B", "Londra"));

        assertThat(correct).isFalse();
    }

    @Test
    void isQuizAnswerCorrectReturnsFalseWhenStudentAnswerIsNullOrBlank() {
        assertThat(quizGradingService.isQuizAnswerCorrect(null, "A", Map.of("A", "Paris"))).isFalse();
        assertThat(quizGradingService.isQuizAnswerCorrect("   ", "A", Map.of("A", "Paris"))).isFalse();
    }

    @Test
    void readStringConvertsNonNullValuesAndPassesThroughNull() {
        assertThat(quizGradingService.readString(42)).isEqualTo("42");
        assertThat(quizGradingService.readString(null)).isNull();
    }

    @Test
    void readIntegerHandlesNumberStringAndFallback() {
        assertThat(quizGradingService.readInteger(5, 0)).isEqualTo(5);
        assertThat(quizGradingService.readInteger("7", 0)).isEqualTo(7);
        assertThat(quizGradingService.readInteger("nu-e-numar", 9)).isEqualTo(9);
        assertThat(quizGradingService.readInteger(null, 9)).isEqualTo(9);
    }

    @Test
    void readBooleanHandlesBooleanStringAndNull() {
        assertThat(quizGradingService.readBoolean(true)).isTrue();
        assertThat(quizGradingService.readBoolean("true")).isTrue();
        assertThat(quizGradingService.readBoolean("altceva")).isFalse();
        assertThat(quizGradingService.readBoolean(null)).isFalse();
    }
}
