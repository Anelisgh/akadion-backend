package com.example.akadion.config;

import com.example.akadion.repository.UserRepository;
import com.example.akadion.security.CsrfCookieFilter;
import com.example.akadion.security.CustomAuthenticationSuccessHandler;
import com.example.akadion.security.CustomAuthorizationRequestResolver;
import com.example.akadion.security.CustomAuthoritiesMapper;
import com.example.akadion.security.StareContFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfigurationSource;

// Această clasă reprezintă "portarul" principal al aplicației. Ea definește cine are voie să intre, 
// pe ce căi, ce filtre de securitate se aplică și cum se face logarea/delogarea.
@Configuration
@EnableWebSecurity // Activăm securitatea web în Spring.
@EnableMethodSecurity // Permite securizarea metodelor individuale folosind adnotații (de ex: @PreAuthorize("hasRole('ADMIN')")).
public class SecurityConfig {

    // Injectăm toate componentele noastre personalizate de securitate.
    private final CustomAuthoritiesMapper customAuthoritiesMapper;
    private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final CorsConfigurationSource corsConfigurationSource;
    private final UserRepository userRepository;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    public SecurityConfig(CustomAuthoritiesMapper customAuthoritiesMapper,
                          CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler,
                          ClientRegistrationRepository clientRegistrationRepository,
                          CorsConfigurationSource corsConfigurationSource,
                          UserRepository userRepository) {
        this.customAuthoritiesMapper = customAuthoritiesMapper;
        this.customAuthenticationSuccessHandler = customAuthenticationSuccessHandler;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.corsConfigurationSource = corsConfigurationSource;
        this.userRepository = userRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // Handler special pentru securitatea CSRF (necesar pentru noul mod în care Spring tratează token-urile leneșe/deferred).
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

        // Handler pentru delogare automată și din Keycloak.
        // După ce utilizatorul se deloghează, Keycloak îl va trimite înapoi pe adresa de frontend (ex: http://localhost:5173).
        OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        logoutSuccessHandler.setPostLogoutRedirectUri(frontendBaseUrl);

        http
            // 1. Configurăm regulile CORS stabilite în CorsConfig.
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            
            // 2. Protecția CSRF (Cross-Site Request Forgery). 
            // Folosim CookieCsrfTokenRepository.withHttpOnlyFalse() pentru ca browserul să poată citi cookie-ul cu Javascript
            // și să îl pună în header-ul request-urilor (folosit de Axios).
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfRequestHandler)
            )
            
            // 3. Adăugăm filtrul nostru de CSRF imediat după filtrele de autentificare de bază.
            // Acest filtru forțează scrierea cookie-ului CSRF la primul request GET.
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            
            // 4. Adăugăm filtrul nostru personalizat de stări cont (StareContFilter).
            // Acesta va verifica dacă utilizatorul logat are contul ACTIV, PENDING, RESPINS sau INCOMPLET 
            // și îi va bloca accesul la API dacă nu este aprobat.
            .addFilterAfter(new StareContFilter(userRepository), CsrfCookieFilter.class)
            
            // 5. Regulile de acces pe căi URL.
            .authorizeHttpRequests(auth -> auth
                // Permitem oricui (fără login) accesul la căile de eroare și monitorizare.
                .requestMatchers("/error", "/actuator/health").permitAll()
                // Orice alt request din aplicație cere obligatoriu ca utilizatorul să fie autentificat (logat).
                .anyRequest().authenticated()
            )
            
            // 6. Integrarea cu Keycloak pentru Login (OAuth2 Login).
            .oauth2Login(oauth2 -> oauth2
                // Specificăm resolver-ul nostru custom (CustomAuthorizationRequestResolver)
                // ca să redirecționăm automat userii la register direct pe interfața Keycloak când accesează calea respectivă.
                .authorizationEndpoint(ep -> ep
                    .authorizationRequestResolver(new CustomAuthorizationRequestResolver(clientRegistrationRepository)))
                // Mapăm rolurile din baza noastră de date locală pe baza UUID-ului din token.
                .userInfoEndpoint(userInfo -> userInfo
                    .userAuthoritiesMapper(customAuthoritiesMapper))
                // Folosim handler-ul nostru custom care verifică starea contului după login
                // și trimite utilizatorul pe ruta corectă de React (ex: /complete-profile, /asteptare-aprobare, etc.).
                .successHandler(customAuthenticationSuccessHandler)
            )
            
            // 7. Configurăm comportamentul de logout.
            .logout(logout -> logout
                .logoutSuccessHandler(logoutSuccessHandler)
            );

        return http.build();
    }
}
