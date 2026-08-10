package com.example.akadion.controller;

import com.example.akadion.dto.*;
import com.example.akadion.entity.User;
import com.example.akadion.exception.UserNotFoundException;
import com.example.akadion.repository.UserRepository;
import com.example.akadion.service.StudentCursService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentController {

    private final StudentCursService studentCursService;
    private final UserRepository userRepository;

    @PostMapping("/cursuri/{cursId}/inscriere")
    @ResponseStatus(HttpStatus.OK)
    public void inscriereCurs(@PathVariable Long cursId, @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        studentCursService.inscriereCurs(user.getId(), cursId);
    }

    @PostMapping("/cursuri/{cursId}/retragere")
    @ResponseStatus(HttpStatus.OK)
    public void retragereCurs(@PathVariable Long cursId, @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        studentCursService.retragereCurs(user.getId(), cursId);
    }

    @GetMapping("/cursuri/disponibile")
    public List<CursDisponibilResponseDto> listaCursuriDisponibile(@AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        return studentCursService.listaCursuriDisponibile(user.getId());
    }

    @GetMapping("/cursuri/mele")
    public List<CursInrolatResponseDto> listaCursuriInrolate(@AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        return studentCursService.listaCursuriInrolate(user.getId());
    }

    @GetMapping("/cursuri/{cursId}/saptamani")
    public List<SaptamanaStudentResponseDto> listaSaptamaniCurs(@PathVariable Long cursId, @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        return studentCursService.listaSaptamaniCurs(user.getId(), cursId);
    }

    @PostMapping("/saptamani/{saptamanaId}/complete")
    @ResponseStatus(HttpStatus.OK)
    public void bifeazaSaptamana(@PathVariable Long saptamanaId, @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        studentCursService.bifeazaSaptamana(user.getId(), saptamanaId);
    }

    @DeleteMapping("/saptamani/{saptamanaId}/complete")
    @ResponseStatus(HttpStatus.OK)
    public void debifeazaSaptamana(@PathVariable Long saptamanaId, @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        studentCursService.debifeazaSaptamana(user.getId(), saptamanaId);
    }

    @GetMapping("/saptamani/{saptamanaId}/documente")
    public List<DocumentStudentResponseDto> listaDocumenteSaptamana(@PathVariable Long saptamanaId, @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        return studentCursService.listaDocumenteSaptamana(user.getId(), saptamanaId);
    }

    @GetMapping("/cursuri/{cursId}/profesor")
    public ProfesorDetaliiResponseDto detaliiProfesorCurs(@PathVariable Long cursId, @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        return studentCursService.detaliiProfesorCurs(user.getId(), cursId);
    }

    @PostMapping("/cursuri/{cursId}/chat")
    public AkyChatResponseDto chatAky(
            @PathVariable Long cursId,
            @org.springframework.validation.annotation.Validated @jakarta.validation.Valid @RequestBody AkyChatRequestDto request,
            @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        return studentCursService.intreabaAky(user.getId(), cursId, request);
    }

    @GetMapping("/cursuri/{cursId}/documente-accesibile")
    public List<AkySursaDocumentDto> listaDocumenteAccesibile(
            @PathVariable Long cursId,
            @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        return studentCursService.listaDocumenteAccesibile(user.getId(), cursId);
    }

    @PostMapping("/cursuri/{cursId}/quiz/generate")
    public List<Map<String, Object>> genereazaQuiz(
            @PathVariable Long cursId,
            @RequestBody QuizGenerateRequestDto request,
            @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        return studentCursService.genereazaQuiz(user.getId(), cursId, request);
    }

    @PostMapping("/cursuri/{cursId}/flashcards/generate")
    public List<Map<String, Object>> genereazaFlashcards(
            @PathVariable Long cursId,
            @RequestBody FlashcardGenerateRequestDto request,
            @AuthenticationPrincipal OidcUser oidcUser) {
        User user = getLoggedUser(oidcUser);
        return studentCursService.genereazaFlashcards(user.getId(), cursId, request);
    }

    private User getLoggedUser(OidcUser oidcUser) {
        return userRepository.findByIdKeycloak(oidcUser.getSubject())
                .orElseThrow(() -> new UserNotFoundException("Utilizatorul autentificat nu are cont local."));
    }
}
