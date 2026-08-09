package com.example.akadion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.cors.CorsConfigurationSource;

@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.keycloak.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.keycloak.scope=openid,profile,email",
        "spring.security.oauth2.client.registration.keycloak.provider=keycloak",
        "spring.security.oauth2.client.registration.keycloak-register.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.keycloak-register.scope=openid,profile,email",
        "spring.security.oauth2.client.registration.keycloak-register.provider=keycloak",
        "spring.security.oauth2.client.registration.keycloak-admin.authorization-grant-type=client_credentials",
        "spring.security.oauth2.client.registration.keycloak-admin.provider=keycloak"
})
class AkadionApplicationTests {

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private CorsConfigurationSource corsConfigurationSource;

    @Test
    void contextLoads() {
    }

}

