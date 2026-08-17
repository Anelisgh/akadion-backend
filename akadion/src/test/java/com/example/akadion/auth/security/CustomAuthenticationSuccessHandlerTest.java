package com.example.akadion.auth.security;

import com.example.akadion.common.entity.Rol;
import com.example.akadion.common.entity.StareCont;
import com.example.akadion.common.entity.User;
import com.example.akadion.common.repository.UserRepository;
import com.example.akadion.auth.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomAuthenticationSuccessHandlerTest {

    private static final String FRONTEND_BASE_URL = "http://localhost:5173";

    private UserRepository userRepository;
    private UserProfileService userProfileService;
    private CustomAuthenticationSuccessHandler successHandler;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userProfileService = mock(UserProfileService.class);
        successHandler = new CustomAuthenticationSuccessHandler(userRepository, userProfileService);
        ReflectionTestUtils.setField(successHandler, "frontendBaseUrl", FRONTEND_BASE_URL);
    }

    @Test
    void firstLoginCreatesLocalIncompleteUserAndRedirectsToFrontendRoot() throws Exception {
        when(userRepository.findByIdKeycloak("sub-new")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(
                request,
                response,
                new TestingAuthenticationToken(oidcUser("sub-new", "  New.User@Akadion.Test  "), null)
        );

        verify(userProfileService).inregistreazaUserNou("sub-new", "new.user@akadion.test");
        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND_BASE_URL + "/");
    }

    @Test
    void repeatedLoginDoesNotCreateDuplicateAndKeepsExistingData() throws Exception {
        User existingUser = user("sub-existing", "existing@akadion.test", "Existing", "User", "STUDENT", "INCOMPLET");
        when(userRepository.findByIdKeycloak("sub-existing")).thenReturn(Optional.of(existingUser));

        MockHttpServletResponse response = performSuccess("sub-existing", "changed@akadion.test");

        verify(userRepository, never()).save(any(User.class));
        assertThat(existingUser.getMail()).isEqualTo("existing@akadion.test");
        assertThat(existingUser.getNume()).isEqualTo("Existing");
        assertThat(existingUser.getStareCont().getDenumire()).isEqualTo("INCOMPLET");
        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND_BASE_URL + "/");
    }

    @Test
    void activeAdminRedirectsToFrontendRoot() throws Exception {
        when(userRepository.findByIdKeycloak("sub-admin")).thenReturn(Optional.of(user("sub-admin", "admin@akadion.test", null, null, "ADMIN", "ACTIV")));

        MockHttpServletResponse response = performSuccess("sub-admin", "admin@akadion.test");

        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND_BASE_URL + "/");
    }

    @ParameterizedTest
    @CsvSource({
            "STUDENT,ACTIV",
            "PROFESOR,ACTIV",
            "STUDENT,INCOMPLET",
            "PROFESOR,PENDING",
            "STUDENT,RESPINS",
            "PROFESOR,INACTIV"
    })
    void redirectsExistingUsersToFrontendRootRegardlessOfRoleAndStatus(String roleName, String stareName) throws Exception {
        when(userRepository.findByIdKeycloak("sub-user")).thenReturn(Optional.of(user("sub-user", "user@akadion.test", null, null, roleName, stareName)));

        MockHttpServletResponse response = performSuccess("sub-user", "user@akadion.test");

        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND_BASE_URL + "/");
    }

    @Test
    void missingSubjectIsHandledWithControlledJsonError() throws Exception {
        MockHttpServletResponse response = performSuccess(" ", "user@akadion.test");

        verify(userRepository, never()).save(any(User.class));
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("claim-ul sub");
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    void missingEmailIsHandledWithControlledJsonError() throws Exception {
        MockHttpServletResponse response = performSuccess("sub-no-email", " ");

        verify(userRepository, never()).save(any(User.class));
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("email valid");
        assertThat(response.getRedirectedUrl()).isNull();
    }

    private MockHttpServletResponse performSuccess(String subject, String email) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(
                request,
                response,
                new TestingAuthenticationToken(oidcUser(subject, email), null)
        );

        return response;
    }

    private OidcUser oidcUser(String subject, String email) {
        OidcIdToken idToken = new OidcIdToken(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of(IdTokenClaimNames.SUB, subject, "email", email)
        );
        return new DefaultOidcUser(java.util.List.of(), idToken);
    }

    private User user(String sub, String email, String nume, String prenume, String roleName, String stareName) {
        User user = new User();
        user.setId(11L);
        user.setIdKeycloak(sub);
        user.setMail(email);
        user.setNume(nume);
        user.setPrenume(prenume);
        user.setNrRespingeri(0);
        user.setStareCont(stareCont(stareName));

        if (roleName != null) {
            Rol rol = new Rol();
            rol.setDenumire(roleName);
            user.setRol(rol);
        }

        return user;
    }

    private StareCont stareCont(String denumire) {
        StareCont stareCont = new StareCont();
        stareCont.setDenumire(denumire);
        return stareCont;
    }
}
