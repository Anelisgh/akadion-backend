package com.example.akadion.akychat.controller;

import com.example.akadion.akychat.dto.AkyChatRequestDto;
import com.example.akadion.akychat.dto.AkyChatResponseDto;
import com.example.akadion.akychat.dto.FlashcardGenerateRequestDto;
import com.example.akadion.akychat.service.StudentAkyService;
import com.example.akadion.auth.security.CurrentUser;
import com.example.akadion.auth.security.CurrentUserDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentAkyController {

    private final StudentAkyService studentAkyService;

    @PostMapping("/cursuri/{cursId}/chat")
    @PreAuthorize("hasRole('STUDENT')")
    public AkyChatResponseDto chatAky(
            @PathVariable Long cursId,
            @Valid @RequestBody AkyChatRequestDto request,
            @CurrentUser CurrentUserDto user) {
        return studentAkyService.intreabaAky(user.id(), cursId, request);
    }

    @PostMapping("/cursuri/{cursId}/flashcards/generate")
    @PreAuthorize("hasRole('STUDENT')")
    public List<Map<String, Object>> genereazaFlashcards(
            @PathVariable Long cursId,
            @Valid @RequestBody FlashcardGenerateRequestDto request,
            @CurrentUser CurrentUserDto user) {
        return studentAkyService.genereazaFlashcards(user.id(), cursId, request);
    }
}
