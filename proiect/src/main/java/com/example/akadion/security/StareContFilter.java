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
@Slf4j
@RequiredArgsConstructor
public class StareContFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        if ("/error".equals(uri) || uri.startsWith("/actuator/")) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // 1. Request neautentificat sau anonim — trece mai departe (Spring Security gestionează accesul)
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken
                || !(auth.getPrincipal() instanceof OidcUser oidcUser)) {
            filterChain.doFilter(request, response);
            return;
        }

        String sub = oidcUser.getSubject();
        Optional<User> userOpt = userRepository.findByIdKeycloak(sub);

        // 2. Autentificat în Keycloak, dar nu are înregistrare locală în DB -> 403
        if (userOpt.isEmpty()) {
            log.warn("Acces refuzat de StareContFilter: sub={} autentificat în Keycloak dar nu există în DB.", sub);
            sendForbiddenResponse(response, "Utilizatorul nu are un cont înregistrat local.");
            return;
        }

        User user = userOpt.get();
        String stare = user.getStareCont().getDenumire();
        String method = request.getMethod();

        // 3. INCOMPLET: permite doar GET /api/auth/me, POST /api/auth/complete-profile și POST /logout
        if ("INCOMPLET".equals(stare)) {
            if (isMeGetRequest(uri, method) || isCompleteProfilePostRequest(uri, method) || isLogoutRequest(uri, method)) {
                filterChain.doFilter(request, response);
            } else {
                log.warn("Acces blocat pentru sub={} (stare: INCOMPLET) la URI: {}", sub, uri);
                sendForbiddenResponse(response, "Profilul este incomplet. Completați profilul pentru a continua.");
            }
            return;
        }

        // 4. PENDING: permite doar GET /api/auth/me și POST /logout
        if ("PENDING".equals(stare)) {
            if (isMeGetRequest(uri, method) || isLogoutRequest(uri, method)) {
                filterChain.doFilter(request, response);
            } else {
                log.warn("Acces blocat pentru sub={} (stare: PENDING) la URI: {}", sub, uri);
                sendForbiddenResponse(response, "Contul este în așteptare pentru aprobare de către administrator.");
            }
            return;
        }

        // 5. RESPINS: permite doar GET /api/auth/me, POST /api/auth/complete-profile (pentru resubmisie) și POST /logout
        if ("RESPINS".equals(stare)) {
            if (isMeGetRequest(uri, method) || isCompleteProfilePostRequest(uri, method) || isLogoutRequest(uri, method)) {
                filterChain.doFilter(request, response);
            } else {
                log.warn("Acces blocat pentru sub={} (stare: RESPINS) la URI: {}", sub, uri);
                sendForbiddenResponse(response, "Cererea ta de înregistrare a fost respinsă de administrator.");
            }
            return;
        }

        // 6. INACTIV: permite doar GET /api/auth/me și POST /logout
        if ("INACTIV".equals(stare)) {
            if (isMeGetRequest(uri, method) || isLogoutRequest(uri, method)) {
                filterChain.doFilter(request, response);
            } else {
                log.warn("Acces blocat pentru sub={} (stare: INACTIV) la URI: {}", sub, uri);
                sendForbiddenResponse(response, "Contul tău a fost dezactivat de administrator.");
            }
            return;
        }

        // 7. ACTIV: trece liber
        filterChain.doFilter(request, response);
    }

    private boolean isMeGetRequest(String uri, String method) {
        return "/api/auth/me".equals(uri) && "GET".equalsIgnoreCase(method);
    }

    private boolean isCompleteProfilePostRequest(String uri, String method) {
        return "/api/auth/complete-profile".equals(uri) && "POST".equalsIgnoreCase(method);
    }

    private boolean isLogoutRequest(String uri, String method) {
        return "/logout".equals(uri) && "POST".equalsIgnoreCase(method);
    }

    private void sendForbiddenResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":403,\"eroare\":\"" + message + "\"}");
    }
}
