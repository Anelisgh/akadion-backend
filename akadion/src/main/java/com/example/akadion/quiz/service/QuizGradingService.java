package com.example.akadion.quiz.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Normalizarea datelor de quiz venite din RAG (structură nesigură, tip Map<String,Object>) și
// verificarea corectitudinii unui răspuns dat de student. Extras din StudentQuizService — e o
// responsabilitate distinctă (interpretare/grading date brute) de orchestrarea ciclului de viață
// al unei încercări de quiz (fetch, apel RAG, persistare).
@Component
public class QuizGradingService {

    public Map<String, Object> sanitizeQuizQuestion(Map<String, Object> rawQuestion, int index) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        sanitized.put("index", index);
        sanitized.put("intrebare", readString(rawQuestion != null ? rawQuestion.get("intrebare") : null));
        sanitized.put("optiuni", normalizeQuizOptions(rawQuestion != null ? rawQuestion.get("optiuni") : null));
        sanitized.put("raspuns_corect", readString(rawQuestion != null ? rawQuestion.get("raspuns_corect") : null));
        sanitized.put("explicatie", readString(rawQuestion != null ? rawQuestion.get("explicatie") : null));
        return sanitized;
    }

    public Map<String, Object> normalizeQuizOptions(Object rawOptions) {
        Map<String, Object> normalized = new LinkedHashMap<>();

        if (rawOptions instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return normalized;
        }

        if (rawOptions instanceof List<?> rawList) {
            for (int i = 0; i < rawList.size(); i++) {
                normalized.put(String.valueOf((char) ('A' + i)), rawList.get(i));
            }
        }

        return normalized;
    }

    public boolean isQuizAnswerCorrect(String raspunsStudent, String raspunsCorect, Map<String, Object> optiuni) {
        String studentNormalizat = normalizeQuizValue(raspunsStudent);
        String corectNormalizat = normalizeQuizValue(raspunsCorect);
        if (studentNormalizat == null || corectNormalizat == null) {
            return false;
        }

        if (studentNormalizat.equals(corectNormalizat)) {
            return true;
        }

        Object valoareOptiune = optiuni.get(raspunsStudent);
        if (valoareOptiune != null && corectNormalizat.equals(normalizeQuizValue(valoareOptiune.toString()))) {
            return true;
        }

        for (Map.Entry<String, Object> entry : optiuni.entrySet()) {
            if (corectNormalizat.equals(normalizeQuizValue(entry.getValue() != null ? entry.getValue().toString() : null))) {
                return studentNormalizat.equals(normalizeQuizValue(entry.getKey()));
            }
        }

        return false;
    }

    public String readString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public Integer readInteger(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        return fallback;
    }

    public boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }

        if (value != null) {
            return Boolean.parseBoolean(String.valueOf(value));
        }

        return false;
    }

    private String normalizeQuizValue(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase();
    }
}
