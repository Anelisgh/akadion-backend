package com.example.akadion.dto;

import java.util.Map;

public record QuizQuestionProjectionDto(
    Integer index,
    String intrebare,
    Map<String, Object> optiuni
) {}
