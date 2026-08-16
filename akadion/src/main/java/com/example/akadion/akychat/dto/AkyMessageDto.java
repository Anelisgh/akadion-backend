package com.example.akadion.akychat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AkyMessageDto(
    @NotBlank(message = "Sender-ul nu poate fi gol.")
    @Pattern(regexp = "^(user|aky)$", message = "Sender-ul trebuie să fie 'user' sau 'aky'.")
    String sender,

    @NotBlank(message = "Textul mesajului nu poate fi gol.")
    @Size(max = 2000, message = "Textul mesajului din istoric nu poate depăși 2000 de caractere.")
    String text
) {}
