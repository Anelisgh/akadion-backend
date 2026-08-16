package com.example.akadion.akychat.dto;

import java.util.List;

public record ConversatiiPaginateDto(
        List<ConversatieDto> continut,
        boolean areUrmatoarea
) {}
