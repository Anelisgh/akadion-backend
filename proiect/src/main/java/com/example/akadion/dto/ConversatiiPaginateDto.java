package com.example.akadion.dto;

import java.util.List;

public record ConversatiiPaginateDto(
        List<ConversatieDTO> continut,
        boolean areUrmatoarea
) {}
