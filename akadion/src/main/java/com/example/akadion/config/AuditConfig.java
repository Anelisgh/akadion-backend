package com.example.akadion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "auditingDateTimeProvider")
public class AuditConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            // Fără autentificare (ex: DataSeeder la pornirea aplicației) — autorul e "system".
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of("system");
            }

            Object principal = authentication.getPrincipal();

            // Login normal din browser — principal-ul e OidcUser, autorul e UUID-ul Keycloak (subject).
            if (principal instanceof OidcUser oidc) {
                return Optional.of(oidc.getSubject());
            }

            // Apeluri directe cu token JWT (alte servicii).
            if (principal instanceof Jwt jwt) {
                return Optional.of(jwt.getSubject());
            }

            // Teste automate / configurări simple, unde principal-ul e deja un String.
            if (principal instanceof String str) {
                return Optional.of(str);
            }

            return Optional.of("system");
        };
    }

    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
