package com.example.akadion.security;

import com.example.akadion.entity.User;
import com.example.akadion.repository.UserRepository;
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
 * 7. ACTIV -> trece liber către orice endpoint autorizat.
 */
// Acest filtru este ca un "filtru de vamă" la intrarea în granițele API-ului nostru.
// El verifică starea contului fiecărui utilizator logat (ACTIV, PENDING, RESPINS, etc.) 
// și îi blochează accesul la resurse dacă acesta nu a fost aprobat încă de un administrator.
@Slf4j
@RequiredArgsConstructor
public class StareContFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        
        // Pasul 1: Excepții by-pass. 
        // Dacă cererea este pentru sistemul de erori intern al Spring (/error) sau paginile de monitorizare (/actuator/**),
        // lăsăm utilizatorul să treacă direct, fără alte verificări.
        if ("/error".equals(uri) || uri.startsWith("/actuator/")) {
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
        Optional<User> userOpt = userRepository.findByIdKeycloak(sub);

        // Pasul 4: Cazul în care utilizatorul este logat în Keycloak, dar nu există rândul lui în DB-ul local.
        // Îi blocăm accesul cu eroarea 403 (Forbidden) pentru că sistemul BFF nu a putut sincroniza datele.
        if (userOpt.isEmpty()) {
            log.warn("Acces refuzat de StareContFilter: sub={} autentificat în Keycloak dar nu există în DB.", sub);
            sendForbiddenResponse(response, "Utilizatorul nu are un cont înregistrat local.");
            return;
        }

        // Extragem starea contului din baza de date și tipul cererii (GET, POST etc.)
        User user = userOpt.get();
        String stare = user.getStareCont().getDenumire();
        String method = request.getMethod();

        // Pasul 5: Aplicăm regulile de filtrare în funcție de starea contului.

        // Regula A: Contul este INCOMPLET (utilizatorul tocmai s-a înregistrat dar nu a completat numele/rolul).
        // Îi dăm voie să apeleze DOAR: citirea propriilor date (/me), salvarea profilului (/complete-profile) și delogarea (/logout).
        if ("INCOMPLET".equals(stare)) {
            if (isMeGetRequest(uri, method) || isCompleteProfilePostRequest(uri, method) || isLogoutRequest(uri, method)) {
                filterChain.doFilter(request, response); // Trece vama
            } else {
                log.warn("Acces blocat pentru sub={} (stare: INCOMPLET) la URI: {}", sub, uri);
                sendForbiddenResponse(response, "Profilul este incomplet. Completați profilul pentru a continua.");
            }
            return;
        }

        // Regula B: Contul este PENDING (a completat datele și așteaptă decizia adminului).
        // Îi dăm voie să vadă doar cine este (/me) și să se delogheze (/logout). Nu are voie să modifice date.
        if ("PENDING".equals(stare)) {
            if (isMeGetRequest(uri, method) || isLogoutRequest(uri, method)) {
                filterChain.doFilter(request, response);
            } else {
                log.warn("Acces blocat pentru sub={} (stare: PENDING) la URI: {}", sub, uri);
                sendForbiddenResponse(response, "Contul este în așteptare pentru aprobare de către administrator.");
            }
            return;
        }

        // Regula C: Cererea de cont a fost RESPINSĂ de admin.
        // Utilizatorul poate să-și vadă profilul, să se delogheze, sau să re-trimită datele de profil (POST /complete-profile) pentru reevaluare.
        if ("RESPINS".equals(stare)) {
            if (isMeGetRequest(uri, method) || isCompleteProfilePostRequest(uri, method) || isLogoutRequest(uri, method)) {
                filterChain.doFilter(request, response);
            } else {
                log.warn("Acces blocat pentru sub={} (stare: RESPINS) la URI: {}", sub, uri);
                sendForbiddenResponse(response, "Cererea ta de înregistrare a fost respinsă de administrator.");
            }
            return;
        }

        // Regula D: Contul este dezactivat definitiv (INACTIV) de un administrator.
        // Permitem doar verificarea stării (/me) și delogarea (/logout).
        if ("INACTIV".equals(stare)) {
            if (isMeGetRequest(uri, method) || isLogoutRequest(uri, method)) {
                filterChain.doFilter(request, response);
            } else {
                log.warn("Acces blocat pentru sub={} (stare: INACTIV) la URI: {}", sub, uri);
                sendForbiddenResponse(response, "Contul tău a fost dezactivat de administrator.");
            }
            return;
        }

        // Regula E: Contul este ACTIV. Utilizatorul are acces liber la orice alte link-uri autorizate din aplicație.
        filterChain.doFilter(request, response);
    }

    // Funcție ajutătoare: Verifică dacă cererea este un GET pe adresa "/api/auth/me"
    private boolean isMeGetRequest(String uri, String method) {
        return "/api/auth/me".equals(uri) && "GET".equalsIgnoreCase(method);
    }

    // Funcție ajutătoare: Verifică dacă cererea este un POST pe adresa "/api/auth/complete-profile"
    private boolean isCompleteProfilePostRequest(String uri, String method) {
        return "/api/auth/complete-profile".equals(uri) && "POST".equalsIgnoreCase(method);
    }

    // Funcție ajutătoare: Verifică dacă cererea este un POST pe adresa "/logout"
    private boolean isLogoutRequest(String uri, String method) {
        return "/logout".equals(uri) && "POST".equalsIgnoreCase(method);
    }

    // Funcție ajutătoare: Scrie un răspuns JSON curat de eroare 403 (Forbidden) înapoi către frontend-ul React.
    private void sendForbiddenResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":403,\"eroare\":\"" + message + "\"}");
    }
}
