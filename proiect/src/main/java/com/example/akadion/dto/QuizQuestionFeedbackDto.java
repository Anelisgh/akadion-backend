package com.example.akadion.dto;

import java.util.Map;

public record QuizQuestionFeedbackDto(
    Integer index,
    String intrebare,
    Map<String, Object> optiuni,
    String raspunsStudent,
    Boolean esteCorect,
    String raspunsCorect,
    String explicatie
) {}
