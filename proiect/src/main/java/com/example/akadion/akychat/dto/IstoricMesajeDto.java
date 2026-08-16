package com.example.akadion.akychat.dto;

import java.util.List;

public record IstoricMesajeDto(
        List<MesajChatDto> mesaje,
        boolean areMaiMulte,
        Long celMaiVechiIdIncarcat
) {}
