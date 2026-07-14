package com.example.akadion.security;

import com.example.akadion.entity.StareCont;
import com.example.akadion.entity.User;
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
import java.util.Optional;

/**
 * Gestionează redirecționarea după autentificarea cu succes (login sau register).
 * Dacă utilizatorul nu are încă un rând în tabela app_user, îl creează în starea INCOMPLET
 * și îl redirecționează către pagina de completare a profilului din frontend.
 * Altfel, redirecționează utilizatorul la pagina potrivită în funcție de starea contului său.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final StareContRepository stareContRepository;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            response.sendRedirect(frontendBaseUrl + "/login");
            return;
        }

        String sub = oidcUser.getSubject();
        String email = oidcUser.getEmail();

        Optional<User> userOpt = userRepository.findByIdKeycloak(sub);

        String redirectUrl;

        if (userOpt.isEmpty()) {
            // Primul login după înregistrare: creăm utilizatorul cu starea INCOMPLET
            log.info("Primul login pentru sub={}. Se creează rând schelet în DB cu starea INCOMPLET.", sub);
            
            StareCont incomplet = stareContRepository.findByDenumire("INCOMPLET")
                    .orElseThrow(() -> new IllegalStateException("Starea contului 'INCOMPLET' nu a fost găsită în DB"));

            User newUser = new User();
            newUser.setIdKeycloak(sub);
            newUser.setMail(email != null ? email.trim().toLowerCase() : "");
            newUser.setStareCont(incomplet);
            newUser.setNrRespingeri(0);

            userRepository.save(newUser);

            redirectUrl = frontendBaseUrl + "/complete-profile";
        } else {
            User user = userOpt.get();
            String stare = user.getStareCont().getDenumire();
            log.info("User sub={} logat cu succes. Stare cont curentă: {}", sub, stare);

            switch (stare) {
                case "INCOMPLET" -> redirectUrl = frontendBaseUrl + "/complete-profile";
                case "PENDING" -> redirectUrl = frontendBaseUrl + "/asteptare-aprobare";
                case "RESPINS" -> redirectUrl = frontendBaseUrl + "/cerere-respinsa";
                case "INACTIV" -> redirectUrl = frontendBaseUrl + "/cont-dezactivat";
                case "ACTIV" -> redirectUrl = frontendBaseUrl + "/";
                default -> redirectUrl = frontendBaseUrl + "/";
            }
        }

        response.sendRedirect(redirectUrl);
    }
}
