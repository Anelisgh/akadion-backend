package com.example.akadion.config;

import com.example.akadion.auth.security.SecurityUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
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
            request.getHeaders().add("X-User", SecurityUtils.currentUser());
            return execution.execute(request, body);
        };
    }

    private RestClient buildRagClient(String url, String username, String password) {
        return RestClient.builder()
                .baseUrl(url)
                .defaultHeaders(headers -> headers.setBasicAuth(username, password))
                .requestInterceptor(contextInterceptor())
                .build();
    }

    @Bean
    public RestClient ragChatRestClient(
            @Value("${app.rag.base-url}") String baseUrl,
            @Value("${app.rag.auth.username}") String username,
            @Value("${app.rag.auth.password}") String password) {
        return buildRagClient(baseUrl, username, password);
    }

    @Bean
    public RestClient ragEmbedderRestClient(
            @Value("${app.rag.embedder-url}") String embedderUrl,
            @Value("${app.rag.auth.username}") String username,
            @Value("${app.rag.auth.password}") String password) {
        return buildRagClient(embedderUrl, username, password);
    }
}
