package com.example.akadion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class AuditConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of("system");
            }
            Object principal = authentication.getPrincipal();
            if (principal instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser oidc) {
                return Optional.of(oidc.getSubject()); // Keycloak User ID (sub claim) from OAuth2 login
            }
            if (principal instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
                return Optional.of(jwt.getSubject()); // Keycloak User ID (sub claim) from JWT tokens
            }
            if (principal instanceof String str) {
                return Optional.of(str);
            }
            return Optional.of("system");
        };
    }
}
