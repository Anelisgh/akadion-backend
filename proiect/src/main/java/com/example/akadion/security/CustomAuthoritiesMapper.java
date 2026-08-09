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
import java.util.Locale;
import java.util.Optional;

/**
 * Mapează autoritățile Spring Security din rolul stocat în DB.
 * Dacă rolul este null (cazul stării INCOMPLET în faza de înregistrare),
 * returnează o listă goală de autorități fără a bloca autentificarea.
 */
@Slf4j
@Component
@RequiredArgsConstructor
// Această clasă are rolul de a traduce rolul salvat în baza noastră de date locală (DB) 
// în formatul înțeles de Spring Security (autorități / GrantedAuthority).
// Astfel, Spring Security va ști exact dacă utilizatorul conectat este STUDENT, PROFESOR sau ADMIN.
public class CustomAuthoritiesMapper implements GrantedAuthoritiesMapper {

    private static final String ACTIVE_STATE = "ACTIV";

    private final UserRepository userRepository;

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(
            Collection<? extends GrantedAuthority> authorities) {

        // Pasul 1: Căutăm în lista de date primite de la Keycloak obiectul de tip OidcUserAuthority.
        // Avem nevoie de el ca să putem citi UUID-ul utilizatorului (sub).
        Optional<OidcUserAuthority> oidcUserAuthority = authorities.stream()
                .filter(OidcUserAuthority.class::isInstance)
                .map(OidcUserAuthority.class::cast)
                .findFirst();

        // Dacă nu găsim acest obiect de identitate, nu îi dăm nicio permisiune (listă goală).
        if (oidcUserAuthority.isEmpty()) {
            return List.of();
        }

        // Extragem UUID-ul unic (sub) din token-ul de identitate Keycloak.
        String sub = oidcUserAuthority.get().getIdToken().getSubject();

        // Pasul 2: Căutăm utilizatorul în DB-ul local folosind UUID-ul.
        return userRepository.findByIdKeycloak(sub)
                .map(user -> {
                    if (user.getStareCont() == null || user.getStareCont().getDenumire() == null) {
                        log.warn("User cu sub={} are stare_cont lipsă în DB. Nu primește autorități.", sub);
                        return (Collection<? extends GrantedAuthority>) List.<GrantedAuthority>of();
                    }

                    String stareCont = user.getStareCont().getDenumire().trim().toUpperCase(Locale.ROOT);
                    if (!ACTIVE_STATE.equals(stareCont)) {
                        log.debug("Userul cu sub={} are starea {}. Autentificare permisă, dar fără autorități de business.", sub, stareCont);
                        return (Collection<? extends GrantedAuthority>) List.<GrantedAuthority>of();
                    }

                    // Cazul A: Utilizatorul este înregistrat dar nu are rol (este în starea INCOMPLET).
                    // În acest caz, returnăm o listă goală de roluri. El este logat, dar nu poate accesa pagini secrete.
                    if (user.getRol() == null || user.getRol().getDenumire() == null || user.getRol().getDenumire().isBlank()) {
                        log.warn("Userul cu sub={} este ACTIV dar nu are rol valid în DB. Autorități goale.", sub);
                        return (Collection<? extends GrantedAuthority>) List.<GrantedAuthority>of();
                    }

                    // Cazul B: Utilizatorul are un rol definit în DB (de exemplu: 'ADMIN').
                    // Îi construim autoritatea corespunzătoare formatului Spring Security, adăugând prefixul "ROLE_" 
                    // (de ex: din 'ADMIN' rezultă 'ROLE_ADMIN').
                    String roleDenumire = normalizeRoleName(user.getRol().getDenumire());
                    if (roleDenumire == null) {
                        log.warn("Rol invalid '{}' pentru sub={}. Autorități goale.", user.getRol().getDenumire(), sub);
                        return (Collection<? extends GrantedAuthority>) List.<GrantedAuthority>of();
                    }

                    log.debug("Mapare rol din DB pentru sub={}: ROLE_{}", sub, roleDenumire);
                    return (Collection<? extends GrantedAuthority>)
                            List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + roleDenumire));
                })
                // Cazul C: Dacă dintr-un motiv bizar utilizatorul s-a logat în Keycloak dar nu există în DB local.
                // În acest caz, returnăm o listă goală de roluri (fără permisiuni) ca plasă de siguranță.
                // Ce se întâmplă mai departe:
                // 1. În browser (fluxul normal): CustomAuthenticationSuccessHandler va sesiza că lipsește din DB,
                //    îl va crea ca 'INCOMPLET' și îl va trimite la completare profil.
                // 2. În apeluri API directe (Postman/Scripturi): StareContFilter îl va bloca direct cu 403 Forbidden.
                .orElseGet(() -> {
                    log.warn("User cu sub={} autentificat în Keycloak dar negăsit încă în DB.", sub);
                    return List.of();
                });
    }

    private String normalizeRoleName(String roleDenumire) {
        if (roleDenumire == null) {
            return null;
        }

        String normalized = roleDenumire.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }

        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }

        return normalized.isBlank() ? null : normalized;
    }
}
