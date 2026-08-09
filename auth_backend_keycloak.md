# Documentație: Securitate și Autentificare (Pachetul `com.example.akadion.security`)

Acest document descrie modulul de securitate al backend-ului Akadion, incluzând integrarea cu Keycloak, filtrele personalizate, maparea rolurilor și gestiunea stărilor conturilor.

## Context de Business (Fluxul de Autentificare și Înregistrare)
Aplicația utilizează o arhitectură Backend-for-Frontend (BFF) cu OAuth2. Nu există parole stocate în baza de date locală; totul trece prin Keycloak. Baza de date a aplicației este sursa principală de adevăr pentru roluri și stări ale contului.

## Clase de Securitate (`com.example.akadion.security`)

### 1. `CustomAuthenticationSuccessHandler`
- **Rol:** Preia controlul după ce o logare via OIDC (Keycloak) a avut succes.
- **Logica internă:**
  - Caută utilizatorul după `id_keycloak` (Sub) în DB.
  - Dacă **NU există**, creează instantaneu contul: extrage email din OIDC user și-l injectează local cu starea `INCOMPLET`. Apoi, dă HTTP 302 redirect către frontend la `app.frontend.base-url + "/complete-profile"`.
  - Dacă **EXISTĂ**, redirecționează către landing page-ul aplicației (depinde de rol, decizia se poate face din React ulterior bazei, aici redirijează pe home URL).

### 2. `StareContFilter`
- **Rol:** Filtru HTTP (implementează `OncePerRequestFilter`) ce blochează utilizatorii ale căror stări nu sunt 100% validate, indiferent ce token au de la Keycloak.
- **Logica de interceptare:**
  - dacă request-ul vizează URL-uri neprotejate, e liber (de ex `/api/auth/me`, logout, sau resursa de completare a profilului pentru incepători).
  - verifică `StareCont` din User:
    - **ACTIV** = Acces permis total (pe baza rolului din controller).
    - **INCOMPLET** / **PENDING** / **INACTIV** / **RESPINS** = Trimite JSON cu Error 403. Utilizatorul nu poate face mutări neregulate.

### 3. `CustomAuthoritiesMapper`
- **Rol:** Suprapune (override) autoritățile (rolurile) din sesiunea Spring Security bazat pe ce e în DB, ignorând ce ar avea Keycloak în token (sursa noastră unicată e PostgreSQL).
- **Logica internă:**
  - Returnează `List.of(new SimpleGrantedAuthority("ROLE_" + denumireRol))` (ex: `ROLE_ADMIN`).
  - **Edge-case:** Dacă starea nu este `ACTIV`, returnează **colecție goală de autorități**. Acest artificiu brutal asigură eșecul adnotărilor `@PreAuthorize("hasRole(...)")` din controllere.

### 4. `CsrfCookieFilter`
- **Rol:** Forțează inițializarea cookie-ului CSRF.
- **De ce:** Spring Security generează XSRF doar "lazy" (la nevoie). Pentru ca React-ul să aibă cookie-ul trimis de la primul `/api/auth/me`, se impune extragerea manuală `csrfToken.getToken()`.

### 5. `CustomAuthorizationRequestResolver`
- **Rol:** Extensie OAuth2 folosită pentru a forța redirect-ul către pagina de înregistrare direct ădin Keycloak.
- **Implementare:** Dacă ruta interceptată de autentificare se termină în `keycloak-register`, se adaugă programatic variabila `prompt=create` la URL-ul form-ului de login din Keycloak, deschizând fila de `Sign Up`. 
