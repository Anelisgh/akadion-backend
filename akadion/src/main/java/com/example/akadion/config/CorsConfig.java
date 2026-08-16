package com.example.akadion.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configurare CORS.
 * Notă: cu proxy-ul Vite, browserul nu mai face request-uri cross-origin
 * în dev — acest config rămâne util ca fallback și pentru medii de producție.
 */
@Configuration
public class CorsConfig {

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(List.of(frontendBaseUrl));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // X-XSRF-TOKEN e necesar pentru validarea protecției CSRF.
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Authorization"));
        // Expus explicit ca frontend-ul să poată citi unde să facă redirect.
        config.setExposedHeaders(List.of("Location"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
