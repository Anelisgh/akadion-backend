package com.example.akadion.security;

import com.example.akadion.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Mapează autoritățile Spring Security din rolul stocat în DB.
 * Dacă rolul este null (cazul stării INCOMPLET în faza de înregistrare),
 * returnează o listă goală de autorități fără a bloca autentificarea.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthoritiesMapper implements GrantedAuthoritiesMapper {

    private final UserRepository userRepository;

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(
            Collection<? extends GrantedAuthority> authorities) {

        Optional<OidcUserAuthority> oidcUserAuthority = authorities.stream()
                .filter(OidcUserAuthority.class::isInstance)
                .map(OidcUserAuthority.class::cast)
                .findFirst();

        if (oidcUserAuthority.isEmpty()) {
            return List.of();
        }

        String sub = oidcUserAuthority.get().getIdToken().getSubject();

        return userRepository.findByIdKeycloak(sub)
                .map(user -> {
                    if (user.getRol() == null) {
                        log.debug("Userul cu sub={} are starea INCOMPLET (fără rol) — autorități goale.", sub);
                        return (Collection<? extends GrantedAuthority>) List.<GrantedAuthority>of();
                    }
                    String roleDenumire = user.getRol().getDenumire();
                    log.debug("Mapare rol din DB pentru sub={}: ROLE_{}", sub, roleDenumire);
                    return (Collection<? extends GrantedAuthority>)
                            List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + roleDenumire));
                })
                .orElseGet(() -> {
                    log.warn("User cu sub={} autentificat în Keycloak dar negăsit încă în DB.", sub);
                    return List.of();
                });
    }
}
