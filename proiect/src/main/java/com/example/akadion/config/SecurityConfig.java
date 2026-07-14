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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

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

        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

        OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        logoutSuccessHandler.setPostLogoutRedirectUri(frontendBaseUrl);

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfRequestHandler)
            )
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .addFilterAfter(new StareContFilter(userRepository), CsrfCookieFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error", "/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(ep -> ep
                    .authorizationRequestResolver(new CustomAuthorizationRequestResolver(clientRegistrationRepository)))
                .userInfoEndpoint(userInfo -> userInfo
                    .userAuthoritiesMapper(customAuthoritiesMapper))
                .successHandler(customAuthenticationSuccessHandler)
            )
            .logout(logout -> logout
                .logoutSuccessHandler(logoutSuccessHandler)
            );

        return http.build();
    }
}
