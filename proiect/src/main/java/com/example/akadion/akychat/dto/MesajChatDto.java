package com.example.akadion.akychat.dto;

import com.example.akadion.akychat.entity.RolMesaj;
import java.time.OffsetDateTime;

public record MesajChatDto(
    Long id,
    RolMesaj rol,
    String continut,
    String surseFolosite,
    OffsetDateTime createdAt,
    boolean areRaspuns
) {}
