package com.example.akadion.quiz.controller;

import com.example.akadion.auth.security.CurrentUserDto;
import com.example.akadion.quiz.dto.FinalizeazaQuizRequestDto;
import com.example.akadion.quiz.dto.IncercareQuizDetailDto;
import com.example.akadion.quiz.dto.IncercareQuizSummaryDto;
import com.example.akadion.quiz.dto.QuizFinalizatResponseDto;
import com.example.akadion.quiz.dto.QuizGenerateRequestDto;
import com.example.akadion.quiz.dto.QuizGenerateResponseDto;
import com.example.akadion.quiz.service.StudentQuizService;
import com.example.akadion.auth.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentQuizController {

    private final StudentQuizService studentQuizService;

    @PostMapping("/cursuri/{cursId}/quiz/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT')")
    public QuizGenerateResponseDto genereazaQuiz(
            @PathVariable Long cursId,
            @Valid @RequestBody QuizGenerateRequestDto request,
            @CurrentUser CurrentUserDto user) {
        return studentQuizService.genereazaQuiz(user.id(), cursId, request);
    }

    @PostMapping("/quiz/{incercareId}/finalizeaza")
    @PreAuthorize("hasRole('STUDENT')")
    public QuizFinalizatResponseDto finalizeazaQuiz(
            @PathVariable Long incercareId,
            @Valid @RequestBody FinalizeazaQuizRequestDto request,
            @CurrentUser CurrentUserDto user) {
        return studentQuizService.finalizeazaQuiz(user.id(), incercareId, request);
    }

    @GetMapping("/quiz/istoric")
    @PreAuthorize("hasRole('STUDENT')")
    public Page<IncercareQuizSummaryDto> getIstoricQuiz(
            @RequestParam(required = false) Long cursId,
            Pageable pageable,
            @CurrentUser CurrentUserDto user) {
        return studentQuizService.getIstoricQuizStudent(user.id(), cursId, pageable);
    }

    @GetMapping("/quiz/istoric/{incercareId}")
    @PreAuthorize("hasRole('STUDENT')")
    public IncercareQuizDetailDto getDetaliuQuiz(
            @PathVariable Long incercareId,
            @CurrentUser CurrentUserDto user) {
        return studentQuizService.getDetaliuQuizStudent(user.id(), incercareId);
    }

    @DeleteMapping("/quiz/{incercareId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('STUDENT')")
    public void stergeIncercareQuiz(
            @PathVariable Long incercareId,
            @CurrentUser CurrentUserDto user) {
        studentQuizService.stergeIncercareQuiz(user.id(), incercareId);
    }
}
