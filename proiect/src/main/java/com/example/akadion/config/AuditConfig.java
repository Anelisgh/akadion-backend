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
// Activăm Auditarea automată din JPA. Ii spunem să folosească generatorul numit "auditorProvider" de mai jos
// ca să afle cine a creat/modificat un rând în baza de date (pentru câmpurile @CreatedBy / @LastModifiedBy).
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class AuditConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            // Pasul 1: Luăm din memoria Spring Security datele despre conexiunea curentă a utilizatorului.
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            // Pasul 2: Dacă nu avem pe nimeni logat sau conexiunea nu este validă/autentificată 
            // (de exemplu, când aplicația pornește și rulează DataSeeder ca să pună date de test),
            // atunci salvăm numele "system" ca autor al modificării.
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of("system");
            }
            
            // Pasul 3: Dacă există o conexiune, extragem obiectul care reprezintă utilizatorul (identitatea lui).
            Object principal = authentication.getPrincipal();
            
            // Pasul 4: Cazul cel mai des întâlnit (Login-ul normal în browser).
            // Spring Security salvează datele utilizatorului într-un obiect numit "OidcUser".
            // Luăm din el "Subject" (care este UUID-ul unic al utilizatorului generat de Keycloak).
            if (principal instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser oidc) {
                return Optional.of(oidc.getSubject()); 
            }
            
            // Pasul 5: Cazul în care apelurile vin direct cu Token JWT (de exemplu, apeluri din alte servicii).
            // Spring Security pune datele într-un obiect de tip "Jwt". 
            // Extragem tot UUID-ul unic din el.
            if (principal instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
                return Optional.of(jwt.getSubject()); 
            }
            
            // Pasul 6: În caz că utilizatorul este salvat doar ca un simplu text (String)
            // (se poate întâmpla în teste automate sau configurări simple), salvăm acel text direct.
            if (principal instanceof String str) {
                return Optional.of(str);
            }
            
            // Pasul 7: Dacă am ajuns aici și nu s-a potrivit nimic, punem din nou "system" ca siguranță.
            return Optional.of("system");
        };
    }
}
