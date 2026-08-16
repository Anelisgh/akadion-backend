package com.example.akadion.auth.security;

import com.example.akadion.common.entity.Rol;
import com.example.akadion.common.entity.StareCont;
import com.example.akadion.common.entity.User;
import com.example.akadion.common.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomAuthoritiesMapperTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomAuthoritiesMapper customAuthoritiesMapper;

    @Test
    void mapsAdminRoleToRoleAdmin() {
        when(userRepository.findByIdKeycloak("sub-admin")).thenReturn(Optional.of(user("ADMIN", "ACTIV")));

        Collection<? extends GrantedAuthority> authorities = customAuthoritiesMapper.mapAuthorities(oidcAuthorities("sub-admin"));

        assertThat(authorities).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }

    @Test
    void avoidsDuplicatingRolePrefix() {
        when(userRepository.findByIdKeycloak("sub-prefixed")).thenReturn(Optional.of(user("ROLE_ADMIN", "ACTIV")));

        Collection<? extends GrantedAuthority> authorities = customAuthoritiesMapper.mapAuthorities(oidcAuthorities("sub-prefixed"));

        assertThat(authorities).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }

    @Test
    void returnsNoAuthoritiesWhenRoleIsMissing() {
        when(userRepository.findByIdKeycloak("sub-no-role")).thenReturn(Optional.of(user(null, "ACTIV")));

        Collection<? extends GrantedAuthority> authorities = customAuthoritiesMapper.mapAuthorities(oidcAuthorities("sub-no-role"));

        assertThat(authorities).isEmpty();
    }

    @Test
    void returnsNoAuthoritiesWhenUserIsMissingFromDatabase() {
        when(userRepository.findByIdKeycloak("sub-missing")).thenReturn(Optional.empty());

        Collection<? extends GrantedAuthority> authorities = customAuthoritiesMapper.mapAuthorities(oidcAuthorities("sub-missing"));

        assertThat(authorities).isEmpty();
    }

    @Test
    void returnsNoAuthoritiesForNonActiveUsers() {
        when(userRepository.findByIdKeycloak("sub-pending")).thenReturn(Optional.of(user("ADMIN", "PENDING")));

        Collection<? extends GrantedAuthority> authorities = customAuthoritiesMapper.mapAuthorities(oidcAuthorities("sub-pending"));

        assertThat(authorities).isEmpty();
    }

    private List<OidcUserAuthority> oidcAuthorities(String sub) {
        OidcIdToken idToken = new OidcIdToken(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("sub", sub)
        );

        return List.of(new OidcUserAuthority(idToken, new OidcUserInfo(Map.of("sub", sub))));
    }

    private User user(String roleName, String stareName) {
        User user = new User();
        user.setIdKeycloak("sub");
        user.setMail("user@akadion.test");
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
}
