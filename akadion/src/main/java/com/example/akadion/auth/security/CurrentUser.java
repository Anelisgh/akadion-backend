package com.example.akadion.auth.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Injectează un CurrentUserDto (id + rol) pentru utilizatorul autentificat (rezolvat din DB pe
// baza principal-ului OIDC) ca parametru de controller, în loc de @AuthenticationPrincipal OidcUser
// + rezoluție manuală repetată în fiecare metodă. Folosim un DTO, nu entitatea User, ca să nu
// plimbăm o entitate JPA prin stratul web.
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
