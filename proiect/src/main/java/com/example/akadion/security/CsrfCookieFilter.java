package com.example.akadion.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Forțează rezolvarea token-ului CSRF "leneș" (deferred) la fiecare request.
 * Fără acest filtru, cookie-ul XSRF-TOKEN nu se scrie la GET-uri,
 * iar primul POST al frontend-ului ar eșua cu 403.
 * Se înregistrează în SecurityConfig după BasicAuthenticationFilter.
 */
// Acest filtru are un singur scop: să forțeze browserul să primească cookie-ul de securitate CSRF (XSRF-TOKEN) chiar de la prima vizită pe site.
// Extinde OncePerRequestFilter, adică se va rula automat o singură dată pentru fiecare cerere trimisă de utilizator.
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        // Pasul 1: Căutăm token-ul CSRF stocat de Spring sub numele "_csrf".
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        
        // Pasul 2: Dacă există, apelăm .getToken().
        // Spring generează aceste token-uri în mod "leneș" (doar când sunt folosite). 
        // Apelând această metodă, îl forțăm să se activeze și să trimită cookie-ul către browser.
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        
        // Pasul 3: Lăsăm cererea să meargă mai departe la următoarele verificări de securitate.
        filterChain.doFilter(request, response);
    }
}
