package com.example.akadion.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.client.RestClient;

@Configuration
public class RagClientConfig {

    /** Propaga request_id-ul si utilizatorul curent catre serviciile RAG. */
    private static ClientHttpRequestInterceptor contextInterceptor() {
        return (request, body, execution) -> {
            String rid = MDC.get(RequestIdFilter.MDC_KEY);
            if (rid != null) {
                request.getHeaders().add(RequestIdFilter.HEADER, rid);
            }
            request.getHeaders().add("X-User", currentUser());
            return execution.execute(request, body);
        };
    }

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return "anonymous";
        }
        if (auth.getPrincipal() instanceof OidcUser oidc && oidc.getEmail() != null) {
            return oidc.getEmail();
        }
        return auth.getName();
    }

    @Bean
    public RestClient ragChatRestClient(
            @Value("${app.rag.base-url}") String baseUrl,
            @Value("${app.rag.auth.username}") String username,
            @Value("${app.rag.auth.password}") String password) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> headers.setBasicAuth(username, password))
                .requestInterceptor(contextInterceptor())
                .build();
    }

    @Bean
    public RestClient ragEmbedderRestClient(
            @Value("${app.rag.embedder-url}") String embedderUrl,
            @Value("${app.rag.auth.username}") String username,
            @Value("${app.rag.auth.password}") String password) {
        return RestClient.builder()
                .baseUrl(embedderUrl)
                .defaultHeaders(headers -> headers.setBasicAuth(username, password))
                .requestInterceptor(contextInterceptor())
                .build();
    }
}
