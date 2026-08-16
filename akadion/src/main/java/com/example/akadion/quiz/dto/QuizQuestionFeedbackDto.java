package com.example.akadion.quiz.dto;

import java.util.Map;

public record QuizQuestionFeedbackDto(
    Integer index,
    String intrebare,
    Map<String, Object> optiuni,
    String raspunsStudent,
    boolean esteCorect,
    String raspunsCorect,
    String explicatie
) {}
