package com.example.akadion.auth.security;

import com.example.akadion.common.entity.User;
import com.example.akadion.exception.ResursaNegasitaException;
import com.example.akadion.common.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserArgumentResolverTest {

    @Mock
    private UserRepository userRepository;

    private CurrentUserArgumentResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CurrentUserArgumentResolver(userRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void supportsParameterOnlyForAnnotatedUserParameter() throws NoSuchMethodException {
        assertThat(resolver.supportsParameter(annotatedUserParameter())).isTrue();
        assertThat(resolver.supportsParameter(plainUserParameter())).isFalse();
    }

    @Test
    void resolveArgumentReturnsUserFromDbWhenOidcPrincipalMatches() {
        OidcUser oidcUser = oidcUser("sub-123");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(oidcUser, null));

        User user = new User();
        user.setId(7L);
        user.setIdKeycloak("sub-123");
        when(userRepository.findByIdKeycloak("sub-123")).thenReturn(Optional.of(user));

        Object resolved = resolver.resolveArgument(null, null, null, null);

        assertThat(resolved).isEqualTo(new CurrentUserDto(7L, null));
    }

    @Test
    void resolveArgumentThrowsWhenUserHasNoLocalAccount() {
        OidcUser oidcUser = oidcUser("sub-missing");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(oidcUser, null));
        when(userRepository.findByIdKeycloak("sub-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOf(ResursaNegasitaException.class);
    }

    @Test
    void resolveArgumentThrowsWhenNotAuthenticated() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOf(ResursaNegasitaException.class);
    }

    private OidcUser oidcUser(String subject) {
        OidcIdToken idToken = new OidcIdToken(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of(IdTokenClaimNames.SUB, subject)
        );
        return new DefaultOidcUser(List.of(), idToken);
    }

    private MethodParameter annotatedUserParameter() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("withCurrentUser", CurrentUserDto.class);
        return new MethodParameter(method, 0);
    }

    private MethodParameter plainUserParameter() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("withoutAnnotation", CurrentUserDto.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private static class TestController {
        // Corp gol intenționat: metoda există doar ca să-i extragem MethodParameter prin reflecție.
        void withCurrentUser(@CurrentUser CurrentUserDto user) {
        }

        // Corp gol intenționat: metoda există doar ca să-i extragem MethodParameter prin reflecție.
        void withoutAnnotation(CurrentUserDto user) {
        }
    }
}
