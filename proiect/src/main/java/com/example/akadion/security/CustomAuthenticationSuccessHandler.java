package com.example.akadion.security;

import com.example.akadion.entity.StareCont;
import com.example.akadion.entity.User;
import com.example.akadion.exception.ForbiddenOperationException;
import com.example.akadion.repository.StareContRepository;
import com.example.akadion.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

/**
 * Gestionează redirecționarea după autentificarea cu succes (login sau register).
 * Dacă utilizatorul nu are încă un rând în tabela app_user, îl redirecționează către pagina
 * de completare a profilului din frontend, unde va fi creat profilul local.
 * Altfel, redirecționează utilizatorul la pagina potrivită în funcție de starea contului său.
 */
// Clasa aceasta se execută AUTOMAT imediat după ce un utilizator s-a logat sau s-a înregistrat cu succes în Keycloak.
// Rolul ei este să decidă unde să îl trimită pe utilizator în interfața de React (frontend) în funcție de starea contului său.
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final String INCOMPLETE_STATE = "INCOMPLET";

    private final UserRepository userRepository;
    private final StareContRepository stareContRepository;

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

            String redirectUrl;

            // Cazul A: Utilizatorul NU există în baza noastră de date (este prima dată când se loghează după ce s-a înregistrat pe Keycloak).
            if (userOpt.isEmpty()) {
                userRepository.findByMail(email)
                        .ifPresent(existingUser -> {
                            throw new ForbiddenOperationException(
                                    "Există deja un cont local asociat cu emailul " + existingUser.getMail() + ".");
                        });

                StareCont incomplete = stareContRepository.findByDenumire(INCOMPLETE_STATE)
                        .orElseThrow(() -> new IllegalStateException("Starea INCOMPLET lipsește din DB."));

                User user = new User();
                user.setIdKeycloak(sub);
                user.setMail(email);
                user.setStareCont(incomplete);
                user.setNrRespingeri(0);

                userRepository.save(user);
                log.info("Primul login pentru sub={}. Utilizator local creat în starea INCOMPLET.", sub);
                redirectUrl = frontendBaseUrl + "/complete-profile";
            }
            // Cazul B: Utilizatorul există deja în DB (s-a mai logat în trecut).
            else {
                User user = userOpt.get();
                String stare = user.getStareCont().getDenumire(); // Citim starea lui curentă din DB
                log.info("User sub={} logat cu succes. Stare cont curentă: {}", sub, stare);

                // Îl redirecționăm pe pagina specifică stării sale.
                switch (stare) {
                    case "INCOMPLET" -> redirectUrl = frontendBaseUrl + "/complete-profile"; // Încă nu a completat profilul
                    case "PENDING" -> redirectUrl = frontendBaseUrl + "/asteptare-aprobare";   // Așteaptă aprobarea adminului
                    case "RESPINS" -> redirectUrl = frontendBaseUrl + "/cerere-respinsa";     // Cererea a fost respinsă de admin
                    case "INACTIV" -> redirectUrl = frontendBaseUrl + "/cont-dezactivat";     // Contul a fost blocat/dezactivat
                    case "ACTIV" -> redirectUrl = frontendBaseUrl + "/";                       // Cont activ - începe din Home
                    default -> redirectUrl = frontendBaseUrl + "/";
                }
            }

            // Pasul 4: Executăm redirecționarea efectivă a browserului către URL-ul ales de mai sus.
            response.sendRedirect(redirectUrl);
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
        response.getWriter().write("{\"status\":" + status + ",\"eroare\":\"" + message + "\"}");
    }
}
