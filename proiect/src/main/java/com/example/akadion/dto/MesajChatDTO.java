package com.example.akadion.dto;

import com.example.akadion.entity.RolMesaj;
import java.time.OffsetDateTime;

public record MesajChatDTO(
    Long id,
    RolMesaj rol,
    String continut,
    String surseFolosite,
    OffsetDateTime createdAt,
    Boolean areRaspuns
) {}
