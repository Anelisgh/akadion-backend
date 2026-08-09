package com.example.akadion.dto;

import java.util.List;

public record IstoricMesajeDto(
        List<MesajChatDTO> mesaje,
        boolean areMaiMulte,
        Long celMaiVechiIdIncarcat
) {}
