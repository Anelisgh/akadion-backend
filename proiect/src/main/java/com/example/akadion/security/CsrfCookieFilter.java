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
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        // Apelul getToken() forțează rezolvarea și scrie cookie-ul XSRF-TOKEN în răspuns
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
