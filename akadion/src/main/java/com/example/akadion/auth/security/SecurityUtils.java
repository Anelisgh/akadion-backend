package com.example.akadion.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class SecurityUtils {

    private SecurityUtils() {
        // clasa utilitara
    }

    public static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return "anonymous";
        }
        if (auth.getPrincipal() instanceof OidcUser oidc && oidc.getEmail() != null) {
            return oidc.getEmail();
        }
        return auth.getName();
    }
}
