package com.example.akadion.controller;

import com.example.akadion.config.SecurityConfig;
import com.example.akadion.dto.UserPendingDto;
import com.example.akadion.entity.Rol;
import com.example.akadion.entity.StareCont;
import com.example.akadion.entity.User;
import com.example.akadion.exception.GlobalExceptionHandler;
import com.example.akadion.repository.UserRepository;
import com.example.akadion.security.CustomAuthenticationSuccessHandler;
import com.example.akadion.security.CustomAuthoritiesMapper;
import com.example.akadion.service.AdminUserService;
import com.example.akadion.service.AuditLogService;
import com.example.akadion.service.CursService;
import com.example.akadion.service.DocumentService;
import com.example.akadion.service.SaptamanaService;
import com.example.akadion.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.MultiValueMap;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AdminController.class, MeController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AuthSecurityWebMvcTest.OAuth2ClientTestConfig.class})
class AuthSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private AuditLogService auditLogService;

    @MockitoBean
    private CursService cursService;

    @MockitoBean
    private SaptamanaService saptamanaService;

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private UserProfileService userProfileService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private CustomAuthoritiesMapper customAuthoritiesMapper;

    @MockitoBean
    private CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

    @MockitoBean
    private CorsConfigurationSource corsConfigurationSource;

    @Test
    void oauth2AuthorizationKeycloakRedirectsToAuthWithoutPromptCreate() throws Exception {
        MvcResult result = mockMvc.perform(oauth2AuthorizationRequest("/oauth2/authorization/keycloak"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("prompt=create"))))
                .andReturn();

        assertAuthorizationRedirect(result, "keycloak", false);
    }

    @Test
    void oauth2AuthorizationKeycloakRegisterRedirectsToAuthWithPromptCreate() throws Exception {
        MvcResult result = mockMvc.perform(oauth2AuthorizationRequest("/oauth2/authorization/keycloak-register"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("prompt=create")))
                .andReturn();

        assertAuthorizationRedirect(result, "keycloak-register", true);
    }

    @Test
    void adminCanAccessAdminEndpoint() throws Exception {
        when(userRepository.findByIdKeycloak("sub-admin")).thenReturn(Optional.of(user("sub-admin", "ADMIN", "ACTIV")));
        when(adminUserService.listaUtilizatori("PENDING")).thenReturn(List.of(new UserPendingDto(
                10L,
                "Admin",
                "Principal",
                "admin@akadion.test",
                null,
                "ADMIN",
                0,
                "PENDING",
                null
        )));

        mockMvc.perform(get("/api/admin/users")
                        .param("stare", "PENDING")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("sub-admin"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mail").value("admin@akadion.test"));

        verify(adminUserService).listaUtilizatori("PENDING");
    }

    @Test
    void studentCannotAccessAdminEndpoint() throws Exception {
        when(userRepository.findByIdKeycloak("sub-student")).thenReturn(Optional.of(user("sub-student", "STUDENT", "ACTIV")));

        mockMvc.perform(get("/api/admin/users")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("sub-student"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void userWithoutRoleCannotAccessAdminEndpoint() throws Exception {
        when(userRepository.findByIdKeycloak("sub-no-role")).thenReturn(Optional.of(user("sub-no-role", null, "ACTIV")));

        mockMvc.perform(get("/api/admin/users")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("sub-no-role"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void meEndpointReturnsJsonForAuthenticatedUser() throws Exception {
        when(userRepository.findByIdKeycloak("sub-admin")).thenReturn(Optional.of(user("sub-admin", "ADMIN", "ACTIV")));

        mockMvc.perform(get("/api/auth/me")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("sub-admin"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.mail").value("sub-admin@akadion.test"))
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.stareCont").value("ACTIV"));
    }

    @Test
    void meEndpointReturnsForbiddenWhenLocalProfileDoesNotExist() throws Exception {
        when(userRepository.findByIdKeycloak("sub-new")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/me")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("sub-new").claim("email", "new@akadion.test"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.eroare").value("Utilizatorul nu are un cont înregistrat local."));
    }

    @Test
    void studentCannotApproveUsers() throws Exception {
        when(userRepository.findByIdKeycloak("sub-student")).thenReturn(Optional.of(user("sub-student", "STUDENT", "ACTIV")));

        mockMvc.perform(patch("/api/admin/users/5/approve")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("sub-student"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminCanApproveUsersWithCsrf() throws Exception {
        when(userRepository.findByIdKeycloak("sub-admin")).thenReturn(Optional.of(user("sub-admin", "ADMIN", "ACTIV")));
        when(adminUserService.approveUser(5L)).thenReturn(new com.example.akadion.dto.ActionResponseDto("ok"));

        mockMvc.perform(patch("/api/admin/users/5/approve")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("sub-admin"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("ok"));

        verify(adminUserService).approveUser(5L);
    }

    @Test
    void adminCanRejectUsersWithCsrf() throws Exception {
        when(userRepository.findByIdKeycloak("sub-admin")).thenReturn(Optional.of(user("sub-admin", "ADMIN", "ACTIV")));
        when(adminUserService.rejectUser(7L)).thenReturn(new com.example.akadion.dto.ActionResponseDto("respins"));

        mockMvc.perform(patch("/api/admin/users/7/reject")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("sub-admin"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("respins"));

        verify(adminUserService).rejectUser(7L);
    }

    @Test
    void professorCannotRejectUsers() throws Exception {
        when(userRepository.findByIdKeycloak("sub-profesor")).thenReturn(Optional.of(user("sub-profesor", "PROFESOR", "ACTIV")));

        mockMvc.perform(patch("/api/admin/users/5/reject")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("sub-profesor"))
                                .authorities(new SimpleGrantedAuthority("ROLE_PROFESOR")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void unauthenticatedUserGetsUnauthorizedOnApproveWhenCsrfIsPresent() throws Exception {
        mockMvc.perform(patch("/api/admin/users/5/approve")
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void approveWithoutCsrfIsRejectedBeforeController() throws Exception {
        when(userRepository.findByIdKeycloak("sub-admin")).thenReturn(Optional.of(user("sub-admin", "ADMIN", "ACTIV")));

        mockMvc.perform(patch("/api/admin/users/5/approve")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("sub-admin"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(adminUserService);
    }

    @Test
    void adminEndpointsUsePatchNotPostForApprove() throws Exception {
        when(userRepository.findByIdKeycloak("sub-admin")).thenReturn(Optional.of(user("sub-admin", "ADMIN", "ACTIV")));

        mockMvc.perform(post("/api/admin/users/5/approve")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("sub-admin"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(adminUserService);
    }

    @Test
    void pendingAdminIsBlockedByStareContFilterBeforeApprove() throws Exception {
        when(userRepository.findByIdKeycloak("sub-admin-pending")).thenReturn(Optional.of(user("sub-admin-pending", "ADMIN", "PENDING")));

        mockMvc.perform(patch("/api/admin/users/5/approve")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("sub-admin-pending"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.eroare").value("Contul este în așteptare pentru aprobare de către administrator."));

        verifyNoInteractions(adminUserService);
    }

    @Test
    void pendingUserCanLogoutWithGetRequest() throws Exception {
        when(userRepository.findByIdKeycloak("sub-pending")).thenReturn(Optional.of(user("sub-pending", "STUDENT", "PENDING")));

        mockMvc.perform(get("/logout")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("sub-pending"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isFound());
    }

    private User user(String sub, String roleName, String stareName) {
        User user = new User();
        user.setId(1L);
        user.setIdKeycloak(sub);
        user.setMail(sub + "@akadion.test");
        user.setNume("Test");
        user.setPrenume("User");
        user.setNrRespingeri(0);

        StareCont stareCont = new StareCont();
        stareCont.setDenumire(stareName);
        user.setStareCont(stareCont);

        if (roleName != null) {
            Rol rol = new Rol();
            rol.setDenumire(roleName);
            user.setRol(rol);
        }

        return user;
    }

    private void assertAuthorizationRedirect(MvcResult result, String registrationId, boolean expectsPromptCreate) {
        String location = result.getResponse().getHeader("Location");
        assertThat(location).isNotBlank();

        URI uri = URI.create(location);
        assertThat(uri.getPath()).isEqualTo("/realms/Akadion/protocol/openid-connect/auth");

        MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        assertThat(params.getFirst("client_id")).isEqualTo("backend-login");
        assertThat(params.getFirst("response_type")).isEqualTo("code");
        assertThat(params.getFirst("redirect_uri"))
                .isEqualTo("http://localhost:8081/login/oauth2/code/" + registrationId);
        assertThat(params.get("scope")).isNotEmpty();
        assertThat(String.join(" ", params.get("scope"))).contains("openid", "profile", "email");
        assertThat(params.getFirst("state")).isNotBlank();

        if (expectsPromptCreate) {
            assertThat(params.getFirst("prompt")).isEqualTo("create");
        } else {
            assertThat(params.getFirst("prompt")).isNull();
        }
    }

    private RequestBuilder oauth2AuthorizationRequest(String path) {
        return get(path).with(localBackendBaseUrl());
    }

    private RequestPostProcessor localBackendBaseUrl() {
        return request -> {
            request.setScheme("http");
            request.setServerName("localhost");
            request.setServerPort(8081);
            return request;
        };
    }

    @TestConfiguration
    static class OAuth2ClientTestConfig {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(clientRegistration("keycloak"), clientRegistration("keycloak-register"));
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
