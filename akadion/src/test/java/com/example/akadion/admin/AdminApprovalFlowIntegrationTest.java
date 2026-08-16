package com.example.akadion.admin;

import com.example.akadion.common.entity.Rol;
import com.example.akadion.common.entity.User;
import com.example.akadion.common.repository.RolRepository;
import com.example.akadion.common.repository.StareContRepository;
import com.example.akadion.common.repository.UserRepository;
import com.example.akadion.auth.service.KeycloakAdminService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
@AutoConfigureMockMvc
@Transactional
@Import(AdminApprovalFlowIntegrationTest.OAuth2ClientTestConfig.class)
class AdminApprovalFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private StareContRepository stareContRepository;

    @MockitoBean
    private ObjectMapper objectMapper;

    @MockitoBean
    private KeycloakAdminService keycloakAdminService;

    @BeforeEach
    void setUpAdmin() {
        if (userRepository.findByIdKeycloak("sub-admin-approval").isPresent()) {
            return;
        }

        User admin = new User();
        admin.setIdKeycloak("sub-admin-approval");
        admin.setMail("admin.approval@akadion.test");
        admin.setNume("Admin");
        admin.setPrenume("Approval");
        admin.setNrRespingeri(0);
        admin.setRol(rolRepository.findByDenumire("ADMIN").orElseThrow());
        admin.setStareCont(stareContRepository.findByDenumire("ACTIV").orElseThrow());
        userRepository.save(admin);
    }

    @Test
    void approvePendingUserWithSpaCsrfTransitionsStateToActiv() throws Exception {
        User pendingUser = pendingUser("approve.flow@akadion.test", studentRole(), 0);
        Cookie csrfCookie = issueCsrfCookieForAdmin();

        mockMvc.perform(patch("/api/admin/users/{id}/approve", pendingUser.getId())
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .with(oidcLogin()
                                .idToken(token -> token.subject("sub-admin-approval"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Utilizatorul a fost aprobat și activat."));

        User updatedUser = userRepository.findById(pendingUser.getId()).orElseThrow();
        assertThat(updatedUser.getStareCont().getDenumire()).isEqualTo("ACTIV");
        assertThat(updatedUser.getIdKeycloak()).isEqualTo(pendingUser.getIdKeycloak());
    }

    @Test
    void rejectPendingUserWithSpaCsrfTransitionsStateToRespinsAndIncrementsCounter() throws Exception {
        User pendingUser = pendingUser("reject.flow@akadion.test", professorRole(), 2);
        Cookie csrfCookie = issueCsrfCookieForAdmin();

        mockMvc.perform(patch("/api/admin/users/{id}/reject", pendingUser.getId())
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .with(oidcLogin()
                                .idToken(token -> token.subject("sub-admin-approval"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Utilizatorul a fost respins."));

        User updatedUser = userRepository.findById(pendingUser.getId()).orElseThrow();
        assertThat(updatedUser.getStareCont().getDenumire()).isEqualTo("RESPINS");
        assertThat(updatedUser.getNrRespingeri()).isEqualTo(3);
    }

    private Cookie issueCsrfCookieForAdmin() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("sub-admin-approval"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn();

        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.getValue()).isNotBlank();
        return csrfCookie;
    }

    private User pendingUser(String mail, Rol role, int nrRespingeri) {
        User user = new User();
        user.setIdKeycloak("sub-" + mail);
        user.setMail(mail);
        user.setNume("Pending");
        user.setPrenume("User");
        user.setNrRespingeri(nrRespingeri);
        user.setRol(role);
        user.setStareCont(stareContRepository.findByDenumire("PENDING").orElseThrow());
        return userRepository.save(user);
    }

    private Rol studentRole() {
        return rolRepository.findByDenumire("STUDENT").orElseThrow();
    }

    private Rol professorRole() {
        return rolRepository.findByDenumire("PROFESOR").orElseThrow();
    }

    @TestConfiguration
    static class OAuth2ClientTestConfig {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(
                    clientRegistration("keycloak"),
                    clientRegistration("keycloak-register")
            );
        }

        private ClientRegistration clientRegistration(String registrationId) {
            return ClientRegistration.withRegistrationId(registrationId)
                    .clientId("backend-login")
                    .clientSecret("test-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "profile", "email")
                    .authorizationUri("http://localhost:8080/realms/Akadion/protocol/openid-connect/auth")
                    .tokenUri("http://localhost:8080/realms/Akadion/protocol/openid-connect/token")
                    .jwkSetUri("http://localhost:8080/realms/Akadion/protocol/openid-connect/certs")
                    .issuerUri("http://localhost:8080/realms/Akadion")
                    .userInfoUri("http://localhost:8080/realms/Akadion/protocol/openid-connect/userinfo")
                    .userNameAttributeName("sub")
                    .clientName(registrationId)
                    .build();
        }
    }
}
