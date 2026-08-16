package com.example.akadion.auth.config;

import com.example.akadion.common.repository.UserRepository;
import com.example.akadion.auth.security.CsrfCookieFilter;
import com.example.akadion.auth.security.CustomAuthenticationSuccessHandler;
import com.example.akadion.auth.security.CustomAuthorizationRequestResolver;
import com.example.akadion.auth.security.CustomAuthoritiesMapper;
import com.example.akadion.auth.security.StareContFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.util.UriComponentsBuilder;

// Această clasă reprezintă "portarul" principal al aplicației. Ea definește cine are voie să intre,
// pe ce căi, ce filtre de securitate se aplică și cum se face logarea/delogarea.
@Configuration
@EnableWebSecurity // Activăm securitatea web în Spring.
@EnableMethodSecurity // Permite securizarea metodelor individuale folosind adnotații (de ex: @PreAuthorize("hasRole('ADMIN')")).
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String LOGOUT_SUCCESS_PATH = "/logout-success";

    // Injectăm toate componentele noastre personalizate de securitate.
    private final CustomAuthoritiesMapper customAuthoritiesMapper;
    private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final CorsConfigurationSource corsConfigurationSource;
    private final UserRepository userRepository;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Value("${app.keycloak.browser-base-url:http://localhost:8080}")
    private String keycloakBrowserBaseUrl;

    @Value("${app.keycloak.realm}")
    private String keycloakRealm;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {

        http
            // 1. Configurăm regulile CORS stabilite în CorsConfig.
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            
            // 2. Protecția CSRF (Cross-Site Request Forgery).
            // Aplicația este SPA și trimite tokenul brut din cookie-ul XSRF-TOKEN în header-ul X-XSRF-TOKEN.
            // În Spring Security 7, csrf.spa() configurează exact acest flux: cookie repository + request handler compatibil cu SPA.
            .csrf(csrf -> csrf.spa())
            
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
                .requestMatchers("/error", "/actuator/health", "/oauth2/**", "/login/**").permitAll()
                // Orice alt request din aplicație cere obligatoriu ca utilizatorul să fie autentificat (logat).
                .anyRequest().authenticated()
            )

            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    PathPatternRequestMatcher.withDefaults().matcher("/api/**")
                )
                .accessDeniedHandler(jsonAccessDeniedHandler())
            )
            
            // 6. Integrarea cu Keycloak pentru Login (OAuth2 Login).
            .oauth2Login(oauth2 -> oauth2
                // Specificăm resolver-ul nostru custom pentru a adăuga parametri OIDC specifici
                // fluxului de înregistrare, fără să ieșim din endpoint-ul standard /auth.
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
                // Logout-ul este declanșat prin navigare completă din SPA, ca browserul să urmeze redirect-ul OIDC până înapoi în frontend.
                .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher("/logout"))
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                .logoutSuccessHandler(keycloakLogoutSuccessHandler())
            );

        return http.build();
    }

    private LogoutSuccessHandler keycloakLogoutSuccessHandler() {
        return (request, response, authentication) -> {
            String logoutSuccessUrl = frontendBaseUrl + LOGOUT_SUCCESS_PATH;

            if (!(authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser)) {
                response.sendRedirect(logoutSuccessUrl);
                return;
            }

            String logoutUrl = UriComponentsBuilder.fromUriString(keycloakBrowserBaseUrl)
                    .pathSegment("realms", keycloakRealm, "protocol", "openid-connect", "logout")
                    .queryParam("id_token_hint", oidcUser.getIdToken().getTokenValue())
                    .queryParam("post_logout_redirect_uri", frontendBaseUrl)
                    .build()
                    .encode()
                    .toUriString();

            response.sendRedirect(logoutUrl);
        };
    }

    @Bean
    AccessDeniedHandler jsonAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":403,\"eroare\":\"Nu ai permisiunea necesară pentru această acțiune.\"}");
        };
    }
}
