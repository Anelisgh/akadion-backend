# Documentație: Securitate și Autentificare (Pachetul `com.example.akadion.auth.security`)

> Actualizat 2026-08-13 — pachetul a fost mutat din `com.example.akadion.security` (layer) în `com.example.akadion.auth.security` (feature `auth`) în cadrul restructurării package-by-feature.

Acest document descrie modulul de securitate al backend-ului Akadion, incluzând integrarea cu Keycloak, filtrele personalizate, maparea rolurilor și gestiunea stărilor conturilor.

## Context de Business (Fluxul de Autentificare și Înregistrare)
Aplicația utilizează o arhitectură Backend-for-Frontend (BFF) cu OAuth2. Nu există parole stocate în baza de date locală; totul trece prin Keycloak. Baza de date a aplicației este sursa principală de adevăr pentru roluri și stări ale contului.

---

## Modelul de sesiune: ce e în cookie, și ce NU e

O întrebare care apare des: "folosim cookie HTTP pentru tokenul de autentificare?" — răspunsul scurt e **da, dar nu direct**. Merită detaliat, pentru că e diferit de modelul clasic SPA + JWT.

### Ce ajunge efectiv în browser

| Cookie | Conține | Citibil din JS? | Scop |
|---|---|---|---|
| `JSESSIONID` | Un identificator opac de sesiune (nu tokenul propriu-zis) | Nu — `HttpOnly` (implicit `true` în Spring Boot) | Leagă browser-ul de sesiunea HTTP păstrată pe server |
| `XSRF-TOKEN` | Un token anti-CSRF | Da — intenționat, citit de Axios și retrimis pe header-ul `X-XSRF-TOKEN` la fiecare cerere care modifică date | Protecție CSRF (double-submit cookie pattern), nu autentificare |

**Tokenurile Keycloak (access token, refresh token, id token) nu ajung niciodată în browser.** Rămân stocate exclusiv server-side, în `OAuth2AuthorizedClientRepository`-ul standard al Spring Security (implicit legat de sesiunea HTTP din memoria serverului), indexate după `JSESSIONID`. Când backend-ul are nevoie de un token (ex: pentru a apela Keycloak Admin API), îl recuperează intern din acel repository — browser-ul doar trimite cookie-ul de sesiune, fără să știe ce se află în spatele lui.

### De ce a fost ales acest model în locul unui JWT păstrat de frontend (`localStorage` / header `Authorization`)

Amenințarea cea mai frecventă la o aplicație SPA e XSS (un script injectat printr-o dependență compromisă, un câmp needigienizat etc.), nu furtul cookie-ului în sine. Diferența dintre cele două modele, exact în acest scenariu:

- **JWT în `localStorage`**: un script injectat citește direct tokenul (JS are acces total la `localStorage`) și îl poate folosi ca și cum ar fi utilizatorul legitim, pe toată durata lui de viață. Revocarea e greu de implementat, pentru că un JWT e valid de sine stătător până expiră (ai nevoie de o listă de blocare separată, ceea ce anulează avantajul de "stateless").
- **Sesiune server-side + cookie `HttpOnly` (varianta curentă)**: un script injectat nu poate citi `JSESSIONID` (blocat de flag-ul `HttpOnly`), deci n-are ce fura la capitolul autentificare. Revocarea e trivială — se invalidează sesiunea pe server, iar cookie-ul din browser devine inutil instant.

### Costul acestei alegeri

Sesiunea e stare ținută pe server, nu "stateless" ca un JWT — dacă backend-ul se scalează pe orizontală (mai multe instanțe în spatele unui load balancer), e nevoie fie de sticky sessions la nivelul proxy-ului, fie de un magazin de sesiuni partajat (`spring-session-data-redis`). Deja notat ca cerință de producție în `auth_infrastructure_deployment.md` Anexa C §4 — nu e o slăbiciune de securitate, doar o limitare operațională cunoscută, relevantă abia când aplicația depășește o singură instanță.

### Ce mai trebuie strict setat înainte de producție

`server.servlet.session.cookie.secure` nu apare explicit în `application.properties` — local (HTTP simplu) nu are cum să fie activat oricum. `http-only` e deja `true` implicit. Înainte de a rula pe HTTPS, trebuie forțat explicit `secure=true` (detalii și exemplu de configurare: `auth_infrastructure_deployment.md` Anexa C §1).

---

## Clase de Securitate (`com.example.akadion.auth.security`)

### 1. `CustomAuthenticationSuccessHandler`
- **Rol:** Preia controlul după ce o logare via OIDC (Keycloak) a avut succes.
- **Logica internă:**
  - Caută utilizatorul după `id_keycloak` (Sub) în DB.
  - Dacă **NU există**, creează instantaneu contul: extrage email din OIDC user și-l injectează local cu starea `INCOMPLET`.
  - **În ambele cazuri** (cont nou sau existent), face HTTP 302 redirect la un singur URL fix: `app.frontend.base-url + "/"` — nu la o rută specifică stării contului.
- **De ce nu decide ruta exactă:** frontend-ul are deja propriul mecanism (`routeByState` + `RequireAuthenticatedState` în `App.jsx`) care citește `stareCont` din `GET /api/auth/me` și redirecționează client-side la pagina corectă — mecanism necesar oricum pentru orice schimbare de stare mid-sesiune (ex: un admin aprobă contul cât timp userul are tab-ul deschis), nu doar la login. Dacă backend-ul ar mai calcula și el separat aceeași rută, regula `stareCont → pagină` ar exista dublat, riscând să divergă. Detalii complete: `fluxuri/01-autentificare-si-onboarding.md` Pasul 2.

### 2. `StareContFilter`
- **Rol:** Filtru HTTP (implementează `OncePerRequestFilter`) ce blochează utilizatorii ale căror stări nu sunt 100% validate, indiferent ce token au de la Keycloak. `/error` și `/actuator/**` sunt excluse necondiționat.
- **Logica de interceptare per stare** (whitelist strict, restul → 403 JSON):

  | Stare | Acces permis |
  |---|---|
  | autentificat, absent din DB local | 403 direct |
  | `INCOMPLET` | `GET /api/auth/me`, `POST /api/auth/complete-profile`, `/logout` |
  | `PENDING` | `GET /api/auth/me`, `/logout` |
  | `RESPINS` | ca `INCOMPLET` (permite re-submisie) |
  | `INACTIV` | `GET /api/auth/me`, `/logout` |
  | `ACTIV` | acces liber (filtrat mai departe de `@PreAuthorize`) |

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

### 6. `CurrentUser` + `CurrentUserArgumentResolver` (adăugate 2026-08-12/13)
- **Rol:** Elimină duplicarea metodei private `getLoggedUser`, copiată identic în 6 controllere.
- **Implementare:** `CurrentUser` e o adnotație de parametru; `CurrentUserArgumentResolver` implementează `HandlerMethodArgumentResolver` — rezolvă parametrul `@CurrentUser User user` direct la entitatea `User` din DB, pe baza `sub`-ului OIDC din `SecurityContextHolder`. Înregistrat în `config/WebMvcConfig.java`.
- **Edge-case:** Dacă nu există `OidcUser` autentificat sau nu există cont local cu acel `sub`, aruncă `ResursaNegasitaException`.
- **Excepție intenționată:** `MeController` **nu** folosește `@CurrentUser` — a fost păstrat intenționat diferit (ar necesita schimbarea semnăturilor din `UserProfileService`).

### 7. `SecurityUtils`
- **Rol:** Clasă utilitară statică (nu e componentă Spring) — extrage un identificator lizibil al utilizatorului curent din `SecurityContextHolder` (email dacă e `OidcUser`, altfel `auth.getName()`, altfel `"anonymous"`). Folosită pentru logging (ex. `AccessLogFilter`), nu pentru autorizare.
