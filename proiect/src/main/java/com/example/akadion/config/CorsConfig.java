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
// Această clasă se ocupă de regulile de tip CORS (Cross-Origin Resource Sharing).
// Pe scurt: browserul blochează o pagină web (frontend-ul pe portul 5173) să ceară date de la un server (backend-ul pe portul 8081)
// din motive de securitate, pentru că rulează pe adrese/porturi diferite.
// Codul de mai jos spune serverului nostru: "Ai încredere în frontend-ul nostru și lasă-l să comunice cu tine."
public class CorsConfig {

    // Citim adresa oficială a frontend-ului din fișierul application.properties (ex: http://localhost:5173)
    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        
        // Pasul 1: Permitem trimiterea automată a datelor de autentificare (Cookie-uri de sesiune și token-uri de securitate).
        config.setAllowCredentials(true);
        
        // Pasul 2: Autorizăm doar adresa de frontend definită în fișierul de proprietăți. Nimeni altcineva nu are voie să ceară date.
        config.setAllowedOrigins(List.of(frontendBaseUrl));
        
        // Pasul 3: Specificăm ce operațiuni are voie frontend-ul să execute (să ceară date: GET, să trimită date: POST, să actualizeze: PUT, etc.).
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        
        // Pasul 4: Permitem headerele de securitate și date standard. 
        // Cel mai important este "X-XSRF-TOKEN", folosit pentru validarea protecției CSRF (anti-phishing).
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Authorization"));
        
        // Pasul 5: Lăsăm browserul să citească header-ul "Location" din răspuns, ca să știe unde să facă redirect dacă este cazul.
        config.setExposedHeaders(List.of("Location"));

        // Înregistrăm toate aceste reguli de încredere pentru toate căile/link-urile posibile din backend ("/**").
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
