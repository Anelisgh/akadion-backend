package com.example.akadion.controller;

import com.example.akadion.entity.Rol;
import com.example.akadion.entity.StareCont;
import com.example.akadion.entity.User;
import com.example.akadion.repository.RolRepository;
import com.example.akadion.repository.StareContRepository;
import com.example.akadion.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
@Import(CompleteProfileFlowIntegrationTest.OAuth2ClientTestConfig.class)
class CompleteProfileFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private StareContRepository stareContRepository;

    @BeforeEach
    void setUpAdmin() {
        if (userRepository.findByIdKeycloak("sub-admin-flow").isPresent()) {
            return;
        }

        User admin = new User();
        admin.setIdKeycloak("sub-admin-flow");
        admin.setMail("admin.flow@akadion.test");
        admin.setNume("Admin");
        admin.setPrenume("Flow");
        admin.setNrRespingeri(0);
        admin.setRol(rolRepository.findByDenumire("ADMIN").orElseThrow());
        admin.setStareCont(stareContRepository.findByDenumire("ACTIV").orElseThrow());
        userRepository.save(admin);
    }

    @Test
    void completeProfileUpdatesExistingSkeletonUserAndReturnsSerializableDate() throws Exception {
        User skeletonUser = new User();
        skeletonUser.setIdKeycloak("sub-profile-flow");
        skeletonUser.setMail("student.flow@akadion.test");
        skeletonUser.setNrRespingeri(0);
        skeletonUser.setStareCont(stareContRepository.findByDenumire("INCOMPLET").orElseThrow());
        User savedSkeleton = userRepository.save(skeletonUser);

        mockMvc.perform(post("/api/auth/complete-profile")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "nume": "Popescu",
                                  "prenume": "Andrei",
                                  "facultate": "FMI",
                                  "rolDorit": "STUDENT"
                                }
                                """)
                        .with(csrf())
                        .with(oidcLogin()
                                .idToken(token -> token.subject("sub-profile-flow").claim("email", "Student.Flow@Akadion.Test"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.mail").value("student.flow@akadion.test"))
                .andExpect(jsonPath("$.rolDorit").value("STUDENT"))
                .andExpect(jsonPath("$.stare").value("PENDING"))
                .andExpect(jsonPath("$.message").value("Profilul a fost salvat și trimis pentru aprobare."))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        User savedUser = userRepository.findByIdKeycloak("sub-profile-flow").orElseThrow();
        assertThat(savedUser.getId()).isEqualTo(savedSkeleton.getId());
        assertThat(savedUser.getMail()).isEqualTo("student.flow@akadion.test");
        assertThat(savedUser.getRol().getDenumire()).isEqualTo("STUDENT");
        assertThat(savedUser.getStareCont().getDenumire()).isEqualTo("PENDING");
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(userRepository.findAll().stream()
                .filter(user -> "sub-profile-flow".equals(user.getIdKeycloak()))
                .count()).isEqualTo(1L);
    }

    @Test
    void completeProfileIsForbiddenWhenLocalSkeletonUserIsMissing() throws Exception {
        mockMvc.perform(post("/api/auth/complete-profile")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "nume": "Popescu",
                                  "prenume": "Andrei",
                                  "facultate": "FMI",
                                  "rolDorit": "STUDENT"
                                }
                                """)
                        .with(csrf())
                        .with(oidcLogin()
                                .idToken(token -> token.subject("sub-missing-profile").claim("email", "missing.profile@akadion.test"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.eroare").value("Utilizatorul nu are un cont înregistrat local."));
    }

    @Test
    void pendingUserAppearsInAdminListWithSerializableDate() throws Exception {
        StareCont pending = stareContRepository.findByDenumire("PENDING").orElseThrow();
        Rol studentRole = rolRepository.findByDenumire("STUDENT").orElseThrow();

        User pendingUser = new User();
        pendingUser.setIdKeycloak("sub-admin-list-pending");
        pendingUser.setMail("pending.list@akadion.test");
        pendingUser.setNume("Pending");
        pendingUser.setPrenume("Listed");
        pendingUser.setFacultate("FMI");
        pendingUser.setNrRespingeri(0);
        pendingUser.setRol(studentRole);
        pendingUser.setStareCont(pending);
        userRepository.save(pendingUser);

        mockMvc.perform(get("/api/admin/users")
                        .param("stare", "PENDING")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("sub-admin-flow").claim("email", "admin.flow@akadion.test"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].mail").value("pending.list@akadion.test"))
                .andExpect(jsonPath("$[0].stare").value("PENDING"))
                .andExpect(jsonPath("$[0].createdAt").isString())
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
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
