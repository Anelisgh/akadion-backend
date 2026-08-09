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

// Aceast─â clas─â reprezint─â "portarul" principal al aplica╚¢iei. Ea define╚Öte cine are voie s─â intre, 
// pe ce c─âi, ce filtre de securitate se aplic─â ╚Öi cum se face logarea/delogarea.
@Configuration
@EnableWebSecurity // Activ─âm securitatea web ├«n Spring.
@EnableMethodSecurity // Permite securizarea metodelor individuale folosind adnota╚¢ii (de ex: @PreAuthorize("hasRole('ADMIN')")).
public class SecurityConfig {

    private static final String LOGOUT_SUCCESS_PATH = "/logout-success";

    // Inject─âm toate componentele noastre personalizate de securitate.
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

        http
            // 1. Configur─âm regulile CORS stabilite ├«n CorsConfig.
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            
            // 2. Protec╚¢ia CSRF (Cross-Site Request Forgery).
            // Aplica╚¢ia este SPA ╚Öi trimite tokenul brut din cookie-ul XSRF-TOKEN ├«n header-ul X-XSRF-TOKEN.
            // ├Än Spring Security 7, csrf.spa() configureaz─â exact acest flux: cookie repository + request handler compatibil cu SPA.
            .csrf(csrf -> csrf.spa())
            
            // 3. Ad─âug─âm filtrul nostru de CSRF imediat dup─â filtrele de autentificare de baz─â.
            // Acest filtru for╚¢eaz─â scrierea cookie-ului CSRF la primul request GET.
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            
            // 4. Ad─âug─âm filtrul nostru personalizat de st─âri cont (StareContFilter).
            // Acesta va verifica dac─â utilizatorul logat are contul ACTIV, PENDING, RESPINS sau INCOMPLET 
            // ╚Öi ├«i va bloca accesul la API dac─â nu este aprobat.
            .addFilterAfter(new StareContFilter(userRepository), CsrfCookieFilter.class)
            
            // 5. Regulile de acces pe c─âi URL.
            .authorizeHttpRequests(auth -> auth
                // Permitem oricui (f─âr─â login) accesul la c─âile de eroare ╚Öi monitorizare.
                .requestMatchers("/error", "/actuator/health", "/oauth2/**", "/login/**").permitAll()
                // Orice alt request din aplica╚¢ie cere obligatoriu ca utilizatorul s─â fie autentificat (logat).
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
                // Specific─âm resolver-ul nostru custom pentru a ad─âuga parametri OIDC specifici
                // fluxului de ├«nregistrare, f─âr─â s─â ie╚Öim din endpoint-ul standard /auth.
                .authorizationEndpoint(ep -> ep
                    .authorizationRequestResolver(new CustomAuthorizationRequestResolver(clientRegistrationRepository)))
                // Map─âm rolurile din baza noastr─â de date local─â pe baza UUID-ului din token.
                .userInfoEndpoint(userInfo -> userInfo
                    .userAuthoritiesMapper(customAuthoritiesMapper))
                // Folosim handler-ul nostru custom care verific─â starea contului dup─â login
                // ╚Öi trimite utilizatorul pe ruta corect─â de React (ex: /complete-profile, /asteptare-aprobare, etc.).
                .successHandler(customAuthenticationSuccessHandler)
            )
            
            // 7. Configur─âm comportamentul de logout.
            .logout(logout -> logout
                // Logout-ul este declan╚Öat prin navigare complet─â din SPA, ca browserul s─â urmeze redirect-ul OIDC p├ón─â ├«napoi ├«n frontend.
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
            response.getWriter().write("{\"status\":403,\"eroare\":\"Nu ai permisiunea necesar─â pentru aceast─â ac╚¢iune.\"}");
        };
    }
}