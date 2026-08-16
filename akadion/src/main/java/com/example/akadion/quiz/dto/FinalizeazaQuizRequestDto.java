package com.example.akadion.quiz.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record FinalizeazaQuizRequestDto(
    @NotNull @Valid List<RaspunsIntrebareDto> raspunsuri
) {}
