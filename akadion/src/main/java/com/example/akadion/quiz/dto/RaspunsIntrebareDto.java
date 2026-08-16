package com.example.akadion.quiz.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RaspunsIntrebareDto(
    @NotNull @Min(0) Integer index,
    String raspunsStudent
) {}
