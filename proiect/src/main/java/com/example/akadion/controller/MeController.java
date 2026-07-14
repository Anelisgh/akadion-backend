package com.example.akadion.controller;

import com.example.akadion.dto.UserMeDto;
import com.example.akadion.exception.UserNotFoundException;
import com.example.akadion.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class MeController {

    private final UserRepository userRepository;

    /**
     * Returnează identitatea utilizatorului logat din DB local.
     * Include atât rolul, cât și starea contului (ex. INCOMPLET, PENDING, ACTIV, RESPINS, INACTIV),
     * permițând frontend-ului să afișeze rutele corespunzătoare.
     */
    @GetMapping("/me")
    public UserMeDto me(@AuthenticationPrincipal OidcUser oidcUser) {
        String sub = oidcUser.getSubject();

        return userRepository.findByIdKeycloak(sub)
                .map(user -> new UserMeDto(
                        user.getId(),
                        user.getNume(),
                        user.getPrenume(),
                        user.getMail(),
                        user.getRol() != null ? user.getRol().getDenumire() : null,
                        user.getStareCont().getDenumire()
                ))
                .orElseThrow(() -> new UserNotFoundException(0L));
    }
}
