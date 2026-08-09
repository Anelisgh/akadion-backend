package com.example.akadion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AkyChatRequestDto(
    @NotBlank(message = "Întrebarea nu poate fi goală.")
    @Size(max = 1000, message = "Întrebarea nu poate depăși 1000 de caractere.")
    String intrebare,

    @NotNull
    @Valid
    @Size(max = 10, message = "Istoricul conversației nu poate depăși 10 mesaje.")
    List<AkyMessageDto> istoricConversatie
) {}
