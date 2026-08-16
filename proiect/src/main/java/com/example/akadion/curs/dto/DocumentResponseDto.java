package com.example.akadion.curs.dto;

import com.example.akadion.curs.entity.DocumentStatusIndex;

public record DocumentResponseDto(
        Long id,
        String titlu,
        DocumentStatusIndex statusIndex,
        boolean activ,
        String urlVizualizare,
        String urlDescarcare
) {}
