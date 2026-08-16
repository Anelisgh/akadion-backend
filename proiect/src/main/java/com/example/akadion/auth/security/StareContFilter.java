package com.example.akadion.auth.security;

import com.example.akadion.common.entity.NumeStareCont;
import com.example.akadion.common.entity.User;
import com.example.akadion.common.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Filtru de securitate care verifică starea contului din DB și restricționează accesul la endpoint-uri.
 *
 * Regulă de acces:
 * 1. Neautentificat -> trece mai departe.
 * 2. Autentificat dar inexistent în DB -> 403 Forbidden.
 * 3. INCOMPLET -> permite doar GET /api/auth/me și POST /api/auth/complete-profile; restul -> 403.
 * 4. PENDING -> permite doar GET /api/auth/me; restul -> 403.
 * 5. RESPINS -> permite doar GET /api/auth/me și POST /api/auth/complete-profile (pentru resubmisie); restul -> 403.
 * 6. INACTIV -> permite doar GET /api/auth/me; restul -> 403.
 * 7. ACTIV (sau orice altă stare necunoscută) -> trece liber către orice endpoint autorizat.
 */
@Slf4j
@RequiredArgsConstructor
// Rutele de mai jos sunt căi API interne fixe (nu depind de mediul de rulare), corespund exact
// rutelor mapate în MeController/AuthController — nu au ce căuta în configurare externă.
@SuppressWarnings("java:S1075")
public class StareContFilter extends OncePerRequestFilter {

    // Corespund rutelor mapate în MeController (/api/auth/me) și AuthController (/api/auth/complete-profile).
    // Dacă acele rute se redenumesc, actualizează și aici — nu există altă legătură automată între ele.
    private static final String ME_PATH = "/api/auth/me";
    private static final String COMPLETE_PROFILE_PATH = "/api/auth/complete-profile";
    private static final String LOGOUT_PATH = "/logout";
    private static final String ERROR_PATH = "/error";
    private static final String ACTUATOR_PREFIX = "/actuator/";

    private final UserRepository userRepository;

    private record StareRule(boolean permis, String mesajRefuz) {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();

        // Pasul 1: Excepții by-pass.
        // Dacă cererea este pentru sistemul de erori intern al Spring (/error) sau paginile de monitorizare (/actuator/**),
        // lăsăm utilizatorul să treacă direct, fără alte verificări.
        if (ERROR_PATH.equals(uri) || uri.startsWith(ACTUATOR_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Preluăm starea de autentificare din memoria de securitate a aplicației.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Pasul 2: Dacă utilizatorul nu este deloc logat (neautentificat) sau este un vizitator anonim,
        // îl lăsăm să treacă mai departe. De ce? Pentru că regulile stabilite în SecurityConfig se vor ocupa ulterior de el
        // (de ex: îi vor bloca accesul la pagini secrete și îl vor trimite la login).
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken
                || !(auth.getPrincipal() instanceof OidcUser oidcUser)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Pasul 3: Dacă utilizatorul este logat, luăm UUID-ul lui unic Keycloak (sub) și îl căutăm în DB locală.
        String sub = oidcUser.getSubject();
        String method = request.getMethod();
        Optional<User> userOpt = userRepository.findByIdKeycloak(sub);

        // Pasul 4: Cazul în care utilizatorul este logat în Keycloak, dar nu există rândul lui în DB-ul local.
        // Fluxul corect trebuie să creeze utilizatorul local imediat la primul login; dacă lipseste aici,
        // tratăm situația ca inconsistentă și blocăm requestul.
        if (userOpt.isEmpty()) {
            log.warn("Acces refuzat de StareContFilter: sub={} autentificat în Keycloak dar nu există în DB.", sub);
            sendForbiddenResponse(response, "Utilizatorul nu are un cont înregistrat local.");
            return;
        }

        // Extragem starea contului din baza de date și aplicăm regula de filtrare aferentă.
        String stare = userOpt.get().getStareCont().getDenumire();
        StareRule regula = evalueazaRegula(stare, uri, method);

        if (regula.permis()) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Acces blocat pentru sub={} (stare: {}) la URI: {}", sub, stare, uri);
            sendForbiddenResponse(response, regula.mesajRefuz());
        }
    }

    // Determină dacă cererea curentă este permisă pentru starea contului dată, plus mesajul de refuz aferent.
    // ACTIV (sau orice stare necunoscută) primește acces liber — comportament identic cu fallthrough-ul anterior.
    private StareRule evalueazaRegula(String stare, String uri, String method) {
        if (NumeStareCont.INCOMPLET.name().equals(stare)) {
            boolean permis = isMeGetRequest(uri, method) || isCompleteProfilePostRequest(uri, method) || isLogoutRequest(uri, method);
            return new StareRule(permis, "Profilul este incomplet. Completați profilul pentru a continua.");
        }
        if (NumeStareCont.PENDING.name().equals(stare)) {
            boolean permis = isMeGetRequest(uri, method) || isLogoutRequest(uri, method);
            return new StareRule(permis, "Contul este în așteptare pentru aprobare de către administrator.");
        }
        if (NumeStareCont.RESPINS.name().equals(stare)) {
            boolean permis = isMeGetRequest(uri, method) || isCompleteProfilePostRequest(uri, method) || isLogoutRequest(uri, method);
            return new StareRule(permis, "Cererea ta de înregistrare a fost respinsă de administrator.");
        }
        if (NumeStareCont.INACTIV.name().equals(stare)) {
            boolean permis = isMeGetRequest(uri, method) || isLogoutRequest(uri, method);
            return new StareRule(permis, "Contul tău a fost dezactivat de administrator.");
        }
        return new StareRule(true, null);
    }

    // Funcție ajutătoare: Verifică dacă cererea este un GET pe adresa "/api/auth/me"
    private boolean isMeGetRequest(String uri, String method) {
        return ME_PATH.equals(uri) && "GET".equalsIgnoreCase(method);
    }

    // Funcție ajutătoare: Verifică dacă cererea este un POST pe adresa "/api/auth/complete-profile"
    private boolean isCompleteProfilePostRequest(String uri, String method) {
        return COMPLETE_PROFILE_PATH.equals(uri) && "POST".equalsIgnoreCase(method);
    }

    // Funcție ajutătoare: Verifică dacă cererea este pe adresa "/logout"
    private boolean isLogoutRequest(String uri, String method) {
        return LOGOUT_PATH.equals(uri)
                && ("GET".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method));
    }

    // Funcție ajutătoare: Scrie un răspuns JSON curat de eroare 403 (Forbidden) înapoi către frontend-ul React.
    private void sendForbiddenResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":403,\"eroare\":\"" + message + "\"}");
    }
}
