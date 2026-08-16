package com.example.akadion.auth.security;

import com.example.akadion.common.entity.User;
import com.example.akadion.exception.ResursaNegasitaException;
import com.example.akadion.common.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

// Rezolvă parametrii @CurrentUser din controllere direct la entitatea User din DB, pe baza
// principal-ului OIDC autentificat. Elimină duplicarea metodei private getLoggedUser, care era
// copiată identic în 6 controllere (ConversatieController, CursProfesorController,
// DocumentProfesorController, SaptamanaProfesorController, DocumentAccessController,
// StudentController).
@Component
@RequiredArgsConstructor
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserRepository userRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && CurrentUserDto.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            throw new ResursaNegasitaException("Utilizatorul autentificat nu are cont local.");
        }

        User user = userRepository.findByIdKeycloak(oidcUser.getSubject())
                .orElseThrow(() -> new ResursaNegasitaException("Utilizatorul autentificat nu are cont local."));

        return new CurrentUserDto(user.getId(), user.getRolDenumire());
    }
}
