package com.example.akadion.curs.controller;

import com.example.akadion.curs.dto.ImageStatusCallbackDto;
import com.example.akadion.curs.entity.DocumentStatusIndex;
import com.example.akadion.curs.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Set;

// Endpoint de tip webhook, apelat de embedder_service (Python), nu de un utilizator logat prin
// Keycloak - de-asta nu foloseste OAuth2/@PreAuthorize ca restul controllerelor, ci Basic Auth
// verificata manual (oglindeste exact verify_credentials din auth.py al embedder-ului).
// Vezi VISION_HANDOFF_SPRINGBOOT.md pentru contractul complet.
@Slf4j
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagCallbackController {

    private static final Set<String> STATUSURI_IMAGINI_PERMISE = Set.of("INDEXED", "FAILED_IMAGES");

    private final DocumentService documentService;

    @Value("${app.rag.callback.username:}")
    private String callbackUsername;

    @Value("${app.rag.callback.password:}")
    private String callbackPassword;

    @PatchMapping("/documents/image-status")
    public ResponseEntity<Void> actualizeazaStatusImagini(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ImageStatusCallbackDto payload) {

        if (!autentificareValida(authorizationHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!STATUSURI_IMAGINI_PERMISE.contains(payload.status())) {
            throw new IllegalArgumentException("status invalid: trebuie sa fie INDEXED sau FAILED_IMAGES.");
        }

        documentService.actualizeazaStatusImagini(
                payload.documentId(),
                DocumentStatusIndex.valueOf(payload.status()),
                payload.imagesIndexed(),
                payload.imagesFailed()
        );

        return ResponseEntity.ok().build();
    }

    private boolean autentificareValida(String authorizationHeader) {
        if (callbackUsername.isBlank() || callbackPassword.isBlank()) {
            log.error("Callback embedder respins: app.rag.callback.username/password nu sunt configurate.");
            return false;
        }

        if (authorizationHeader == null || !authorizationHeader.startsWith("Basic ")) {
            return false;
        }

        String utilizatorPrimit;
        String parolaPrimita;
        try {
            String decodat = new String(Base64.getDecoder().decode(authorizationHeader.substring(6)), StandardCharsets.UTF_8);
            String[] parti = decodat.split(":", 2);
            if (parti.length != 2) {
                return false;
            }
            utilizatorPrimit = parti[0];
            parolaPrimita = parti[1];
        } catch (IllegalArgumentException e) {
            return false;
        }

        boolean valid = constantTimeEquals(utilizatorPrimit, callbackUsername) && constantTimeEquals(parolaPrimita, callbackPassword);
        if (!valid) {
            log.warn("Callback embedder respins: credentiale Basic Auth invalide.");
        }
        return valid;
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
