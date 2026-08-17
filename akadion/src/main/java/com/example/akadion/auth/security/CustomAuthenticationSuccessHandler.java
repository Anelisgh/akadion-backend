package com.example.akadion.auth.security;

import com.example.akadion.common.entity.User;
import com.example.akadion.exception.ForbiddenOperationException;
import com.example.akadion.common.repository.UserRepository;
import com.example.akadion.auth.service.UserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Gestionează redirecționarea după autentificarea cu succes (login sau register).
 * Dacă utilizatorul nu are încă un rând în tabela app_user, îl creează local (stare inițială INCOMPLET).
 * În ambele cazuri redirecționează la rădăcina frontend-ului — frontend-ul decide singur pe ce
 * pagină ajunge userul, citind stareCont din GET /api/auth/me (vezi App.jsx: routeByState +
 * RequireAuthenticatedState). Backend-ul nu mai trebuie să cunoască structura de rute a frontend-ului.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final UserProfileService userProfileService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl; // Adresa de React (ex: http://localhost:5173)

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Authentication authentication) throws IOException {

        // Pasul 1: Verificăm dacă utilizatorul logat este de tip OidcUser (specific pentru Keycloak/OAuth2).
        // Dacă nu este, îl trimitem forțat la pagina de login.
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            response.sendRedirect(frontendBaseUrl + "/login");
            return;
        }

        try {
            // Pasul 2: Extragem UUID-ul Keycloak (sub) și adresa de email din token-ul primit.
            String sub = normalizeRequired(oidcUser.getSubject(), "Tokenul utilizatorului nu conține claim-ul sub.");
            String email = normalizeEmail(oidcUser.getEmail());

            // Pasul 3: Căutăm în baza noastră de date locală dacă acest utilizator are deja un cont înregistrat la noi.
            Optional<User> userOpt = userRepository.findByIdKeycloak(sub);

            // Cazul A: Utilizatorul NU există în baza noastră de date (este prima dată când se loghează după ce s-a înregistrat pe Keycloak).
            if (userOpt.isEmpty()) {
                userProfileService.inregistreazaUserNou(sub, email);
                log.info("Cont nou creat local pentru sub={}, stare inițială INCOMPLET.", sub);
            }
            // Cazul B: Utilizatorul există deja în DB (s-a mai logat în trecut).
            else {
                log.info("User sub={} logat cu succes. Stare cont curentă: {}", sub, userOpt.get().getStareCont().getDenumire());
            }

            // Pasul 4: Redirecționăm întotdeauna la rădăcina frontend-ului. Ruta exactă (complete-profile,
            // asteptare-aprobare, cerere-respinsa, cont-dezactivat sau home) e decisă de frontend, pe baza
            // stareCont-ului proaspăt citit din GET /api/auth/me — nu de backend.
            response.sendRedirect(frontendBaseUrl + "/");
        } catch (IllegalArgumentException ex) {
            log.warn("Autentificare OIDC invalidă: {}", ex.getMessage());
            sendJsonError(response, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        } catch (ForbiddenOperationException ex) {
            log.warn("Autentificare OIDC refuzată: {}", ex.getMessage());
            sendJsonError(response, HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
        } catch (IllegalStateException ex) {
            log.error("Configurare locală invalidă pentru autentificare: {}", ex.getMessage(), ex);
            sendJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }

    private String normalizeRequired(String value, String errorMessage) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }

        return normalized;
    }

    private String normalizeEmail(String email) {
        String normalizedEmail = normalizeRequired(email, "Tokenul utilizatorului nu conține un email valid.")
                .toLowerCase(Locale.ROOT);

        if (!isValidEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Tokenul utilizatorului nu conține un email valid.");
        }

        return normalizedEmail;
    }

    private boolean isValidEmail(String email) {
        int atIndex = email.indexOf('@');
        return atIndex > 0 && atIndex == email.lastIndexOf('@') && atIndex < email.length() - 1;
    }

    private void sendJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        String body = objectMapper.writeValueAsString(Map.of("status", status, "eroare", message));
        response.getWriter().write(body);
    }
}
