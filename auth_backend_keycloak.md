# Plan Implementare: Keycloak, Register nativ + Complete-Profile, Login (Spring Boot + React, arhitectură BFF)

## Cum se folosește acest plan

Dă agentului **câte o Etapă odată**, nu tot fișierul. Verifică rezultatul înainte să treci mai departe. Fiecare etapă e marcată:
- 🖱️ **MANUAL** — faci tu, prin consola web Keycloak sau fișiere de temă (un agent de cod nu poate da click prin UI, iar fișierele de temă sunt scrise o singură dată, nu generate repetitiv)
- 🤖 **AGENT** — dai secțiunea unui agent AI de cod

**Presupuneri**: React cu Vite (JS simplu), Maven, package Java `com.example.akadion`, realm Keycloak `akadion`.

⚠️ **Notă importantă despre temă**: nu poți pune componente React literal în paginile Keycloak — Keycloak randează acele pagini server-side, prin propriul sistem de teme (HTML/CSS/FreeMarker). Rezultatul final poate arăta identic cu restul aplicației (aceleași culori/fonturi/layout), doar tehnic e alt motor de randare în spate.

---

## Context arhitectural (citește / dă-i asta agentului înainte de Etapa 1)

### Fluxul complet (nou)

```
1. User apasă "Înregistrare" în React → redirect către Keycloak (pagină cu temă custom)
2. User completează email + parolă direct în Keycloak (parola NU atinge backend-ul vostru)
3. Keycloak creează contul, îl autentifică automat, redirecționează către backend (același callback ca la login normal)
4. Backend detectează: sesiune nouă, dar NU există rând USER pentru acest sub → creează un rând USER "schelet"
   (ID_KEYCLOAK, MAIL completate; NUME/PRENUME/FACULTATE/ID_ROL = NULL; STARE_CONT = INCOMPLET)
   → redirecționează către /complete-profile (React)
5. User completează NUME/PRENUME/FACULTATE/ROL dorit → UPDATE rândul existent, STARE_CONT = PENDING
6. Cererea apare la admin → Acceptă / Respinge
   - Acceptă: STARE_CONT = ACTIV (operație pură de DB — rolul e deja acolo din pasul 5, Keycloak nu stochează rol deloc)
   - Respinge: STARE_CONT = RESPINS. Contul Keycloak NU se atinge — rămâne funcțional
7. Dacă userul RESPINS se loghează din nou (contul Keycloak tot funcționează): vede o pagină
   "cererea a fost respinsă", cu opțiune de a edita și retrimite → UPDATE același rând, STARE_CONT = PENDING din nou
```

### Decizii de arhitectură

- **BFF**: frontend-ul nu vorbește niciodată direct cu Keycloak — orice redirect trece prin backend
- Doi clienți Keycloak, `confidential`: `backend-login` (login + register, autentificare useri) și `backend-admin-api` (service account, operațiuni admin)
- Sesiuni in-memory (fără Redis, o singură instanță de backend)
- CSRF activat (`CookieCsrfTokenRepository` + `CsrfCookieFilter` pentru deferred-token)
- Proxy Vite în dev (elimină CORS/cross-origin complet, cod frontend cu URL-uri relative)
- ⚠️ **Principiu KISS (decizie coordonator)**: Keycloak stochează **doar identitate minimă** — email + parolă, atât. NU stochează rol, NU stochează nume/prenume. DB (`APP_USER`) e singura sursă de adevăr pentru absolut tot ce ține de business — rol, nume, prenume, facultate, stare cont. Backend-ul nu citește niciodată `firstName`/`lastName`/rol din tokenul Keycloak; `CustomAuthoritiesMapper` (Etapa 2.1) construiește autoritățile exclusiv din `ID_ROL` din DB. Consecință directă: nu mai există niciun apel Keycloak legat de rol nicăieri în plan (nici creare roluri de realm, nici atribuire la accept) — vezi Etapa 5.

### Schema DB (actualizată pentru noul flux)

```
ROL
* ID
* DENUMIRE          -- 'ADMIN' | 'PROFESOR' | 'STUDENT'

STARE_CONT
* ID
* DENUMIRE          -- 'INCOMPLET' | 'PENDING' | 'ACTIV' | 'INACTIV' | 'RESPINS'
                        -- INCOMPLET = nou, cont Keycloak creat, profil neincomplet

APP_USER              -- "USER" e cuvânt rezervat în Postgres, folosim alt nume de tabelă
* ID
* ID_KEYCLOAK        -- ⚠️ acum NOT NULL + UNIQUE de la register (nu mai e nullable până la acceptare)
* ID_STARE_CONT       -- FK -> STARE_CONT, NOT NULL
* ID_ROL              -- ⚠️ acum NULLABLE (completat abia la Complete-Profile, nu la register)
* NUME                -- ⚠️ NULLABLE
* PRENUME             -- ⚠️ NULLABLE
* MAIL                -- din Keycloak, la register; UNIQUE (Keycloak deja impune unicitatea nativ)
* FACULTATE           -- ⚠️ NULLABLE
* NR_RESPINGERI        -- opțional, contor incrementat la fiecare respingere (înlocuiește vechea logică de numărare a rândurilor RESPINS, care nu mai are sens — acum există un singur rând per persoană)
* CREATED_BY, CREATED_DATE, LAST_MODIFIED_BY, LAST_MODIFIED_DATE -- (din BaseAuditableEntity, completate prin AuditConfig și OidcUser)
```

⚠️ **Ce dispare față de planul anterior**: toată logica de "MAIL fără UNIQUE strict, verificare aplicativă de duplicate, index parțial, resubmitere ca INSERT nou" (fosta §3c/§3d) — devine inutilă. Acum există **exact un rând `APP_USER` per identitate Keycloak**, creat o singură dată la primul login/register și actualizat ulterior (niciodată un al doilea `INSERT` pentru aceeași persoană). `MAIL` poate fi `UNIQUE` strict în DB fără nicio grijă, fiindcă Keycloak deja garantează unicitatea la register, înainte ca rândul din DB să existe măcar.

---

## Etapa 0 — 🖱️ MANUAL: Configurare Keycloak (rulare locală)

1. Pornește Keycloak: `bin/kc.sh start-dev` (sau `.bat` pe Windows) → `http://localhost:8080`

2. Autentifică-te în Admin Console, creează realm-ul `akadion`

3. **Activează auto-înregistrarea**: Realm settings → tab **Login** → `User registration` = **ON**. Tot aici, `Email as username` = **ON** — face ca username-ul să devină automat identic cu email-ul, deci nu mai apare separat pe formulare ca un câmp în plus de completat.

4. **Curăță User Profile** (Realm settings → tab **User profile**), aliniat cu principiul KISS (Keycloak stochează doar identitate minimă): păstrează `email` (rămâne obligatoriu), **șterge** `firstName` și `lastName` din listă. `username` nu se șterge (e conceptul fundamental de identitate al lui Keycloak, nu un atribut obișnuit) — dar cu `Email as username` activat la pasul anterior, nu mai apare separat pe formularul de register oricum.

⚠️ Verifică după acest pas: pagina de register (o poți accesa direct, vezi Etapa 2.2 pentru URL) ar trebui să ceară acum doar email + parolă, nimic altceva.

5. **Creează clientul `backend-login`** — la pasul "Capability config" din wizard, setează exact:
   - Client authentication: **On**
   - Authorization: Off
   - Standard flow: **On**
   - Direct access grants: **Off** ⚠️ (Keycloak îl pornește implicit la creare — dați-l jos; e grant-ul deprecat "user+parolă direct către Keycloak", ocolește complet fluxul Authorization Code pe care îl folosim)
   - Implicit flow: Off
   - Service accounts roles: Off (e treaba celuilalt client)
   - Standard Token Exchange / OAuth 2.0 Device Authorization Grant / OIDC CIBA Grant: Off (toate)
   - PKCE Method: **S256** ⚠️ (implicit e "Choose..." = fără PKCE — trebuie ales explicit, e recomandarea din context arhitectural, de aici se activează efectiv)
   - Apoi, la pasul următor al wizard-ului: Valid redirect URIs: `http://localhost:8081/login/oauth2/code/keycloak` și `http://localhost:5173/*`; Web origins: `http://localhost:8081`
   - Copiază `client secret` din tab-ul Credentials (după ce clientul e creat)

6. **Creează clientul `backend-admin-api`** — la "Capability config":
   - Client authentication: **On**
   - Authorization: Off
   - Standard flow: **Off** (acest client nu face niciodată login de user cu redirect în browser)
   - Direct access grants / Implicit flow: Off
   - Service accounts roles: **On** ⚠️ (singurul important pentru el — activează grant-ul `client_credentials`)
   - Restul (Token Exchange, Device Authorization, CIBA): Off
   - PKCE Method: nu contează, lasă "Choose..." (nu se aplică fără Standard flow)
   - Tab Service accounts roles → Assign role → din `realm-management` → `manage-users`, `view-users`
   - Copiază `client secret`

7. **Configurează SMTP** pentru realm (Realm settings → Email) — necesar pentru email-uri native Keycloak (confirmare cont, resetare parolă uitată). Pentru local: Mailtrap sau MailHog.

8. ⚠️ **Creează manual primul admin** (Users → Add user, la fel ca înainte — bootstrap-ul de admin nu trece prin fluxul normal, fiindcă n-ar exista cine să-l accepte):
   - Email, tab Credentials → parolă (debifează Temporary)
   - Copiază `ID`-ul userului — ai nevoie de el la Etapa 1.7 (rolul `ADMIN` se atribuie **doar în DB**, prin scriptul de bootstrap, nu în Keycloak)

✅ **Verificare Etapa 0**: `http://localhost:8080/realms/akadion/.well-known/openid-configuration` răspunde cu JSON.

---

## Etapa 0.9 — 🖱️ MANUAL: Temă custom Keycloak pentru pagina de Register

Nu se dă unui agent de cod ca task repetitiv — se scrie o singură dată, HTML/CSS static, ideal chiar de voi (sau agentul poate genera fișierele o dată, ca orice alt fișier static).

1. În folderul unde ai descărcat Keycloak: `themes/akadion-theme/login/`

2. ⚠️ Numele exact al fișierului de template pentru register **diferă în funcție de versiunea Keycloak** (versiunile recente folosesc `register-user-profile.ftl`, versiuni mai vechi `register.ftl`) — verifică în `themes/keycloak/login/` (tema implicită, `base`/`keycloak`) care fișier există acolo și copiază-l ca punct de plecare, nu porni de la zero.

3. Copiază și `theme.properties` din tema `keycloak` de bază, ajustează `parent=keycloak` (moștenește restul paginilor — login, resetare parolă etc. — nemodificate, doar `register` e suprascris)

4. Editează template-ul copiat + adaugă un `resources/css/styles.css` propriu — culori/fonturi/layout identice cu React-ul vostru (odată ce aveți o paletă de culori stabilită în frontend, refolosiți aceleași valori aici, ca experiența să pară continuă)

ℹ️ Dacă ai făcut deja curățenia din Etapa 0 (User Profile fără `firstName`/`lastName`, `Email as username` ON), template-ul copiat la pasul 2 ar trebui să randeze deja doar câmpurile email + parolă, automat — nu mai e nimic de editat suplimentar aici pe partea de câmpuri, doar stilul (CSS).

5. Realm settings → tab **Themes** → `Login theme` = `akadion-theme`

✅ **Verificare Etapa 0.9**: accesând link-ul de register (vezi Etapa 2.2 pentru cum se construiește), pagina arată cu tema voastră, nu cu aspectul implicit Keycloak.

---

## Etapa 1 — 🤖 AGENT: Schema DB + configurare proiect

### 1.1 `backend/pom.xml`
Dependențe: `spring-boot-starter-web`, `spring-boot-starter-oauth2-client`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `org.postgresql:postgresql`, `org.projectlombok:lombok`, `org.mapstruct:mapstruct`.
Configurare procesare adnotări în plugin-ul `maven-compiler-plugin`: `lombok`, `mapstruct-processor` și `lombok-mapstruct-binding`.

### 1.2 `backend/src/main/resources/application.properties`
```properties
spring.application.name=akadion
server.port=8081

# --- DataSource ---
spring.datasource.url=jdbc:postgresql://localhost:5432/akadion
spring.datasource.username=postgres
spring.datasource.password=${DB_PASSWORD}

# --- JPA / Hibernate ---
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# --- Flyway ---
# spring.flyway.enabled=true # Decomentează ulterior când vei folosi migrații

# --- OAuth2 Client (BFF pattern) ---
spring.security.oauth2.client.registration.keycloak-register.client-id=backend-login
spring.security.oauth2.client.registration.keycloak-register.client-secret=${KEYCLOAK_BACKEND_LOGIN_SECRET}
spring.security.oauth2.client.registration.keycloak-register.authorization-grant-type=authorization_code
spring.security.oauth2.client.registration.keycloak-register.scope=openid,profile,email
spring.security.oauth2.client.registration.keycloak-register.provider=keycloak

spring.security.oauth2.client.registration.keycloak-admin.client-id=backend-admin-api
spring.security.oauth2.client.registration.keycloak-admin.client-secret=${KEYCLOAK_ADMIN_API_SECRET}
spring.security.oauth2.client.registration.keycloak-admin.authorization-grant-type=client_credentials
spring.security.oauth2.client.registration.keycloak-admin.provider=keycloak

spring.security.oauth2.client.provider.keycloak.issuer-uri=http://localhost:8080/realms/akadion

app.keycloak.realm=akadion
app.keycloak.base-url=http://localhost:8080
app.frontend.base-url=http://localhost:5173
```
⚠️ Observă `keycloak-register` — o **înregistrare duplicată** a lui `keycloak-login` (același `client-id`/`client-secret`), sub alt nume. Motiv tehnic: rezolvatorul implicit al Spring Security identifică înregistrarea după ultimul segment din URL (`/oauth2/authorization/{id}`) — ca `/oauth2/authorization/keycloak-register` să găsească o configurație validă de pornit, avem nevoie de o intrare cu exact acel nume. Comportamentul diferit (redirect spre `/registrations` în loc de `/auth`) se face separat, în Etapa 2.2.

### 1.3 `application-local.properties.example` + `.gitignore`
Ca înainte — fișier exemplu fără secrete, cel real necomis.

### 1.4 Entități JPA — `backend/src/main/java/com/example/akadion/entity/`
- `Rol.java` (Entitatea ce mapează tabela `roluri`):
```java
@Entity
@Table(name = "roluri")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Rol {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String denumire;
}
```
- `StareCont.java` (Entitatea ce mapează tabela `stari_cont`):
```java
@Entity
@Table(name = "stari_cont")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class StareCont {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String denumire;
}
```
- `User.java` — `@Table(name = "app_user")` (cuvânt rezervat Postgres). Câmpuri: `ID`, `IdKeycloak` (⚠️ acum `nullable = false, unique = true`), `StareCont` (`@ManyToOne`, `NOT NULL`), `Rol` (`@ManyToOne`, ⚠️ **acum nullable**), `NUME`/`PRENUME`/`FACULTATE` (⚠️ **acum nullable**), `MAIL` (`unique = true` — acum sigur, vezi contextul arhitectural), `NrRespingeri` (Integer, default 0, opțional)

### 1.4.1 Audit (JPA Auditing)
- `AuditConfig.java` implementează `AuditorAware<String>` care extrage UUID-ul (`sub`) din `OidcUser` (din `SecurityContextHolder`). Fallback la `"system"` pentru acțiuni fără user.
- Toate entitățile extind `BaseAuditableEntity`.

### 1.5 Repository-uri
- `RolRepository`, `StareContRepository` — `findByDenumire(String)`
- `UserRepository` — `findByIdKeycloak(String)`, `findByMail(String)` (returnează `Optional<User>`)

### 1.6 Seed date inițiale — `DataSeeder.java`
`CommandLineRunner`, la pornire, dacă `ROL`/`STARE_CONT` sunt goale, inserează:
- `ROL`: `ADMIN`, `PROFESOR`, `STUDENT`
- `STARE_CONT`: `INCOMPLET`, `PENDING`, `ACTIV`, `INACTIV`, `RESPINS`

### 1.7 — 🖱️ MANUAL: Bootstrap primul admin în DB

La fel ca înainte — script separat, nu în `DataSeeder` (UUID diferit per persoană din echipă).

`backend/scripts/bootstrap-admin.sql` (șablon, comis fără valori reale completate):
```sql
INSERT INTO app_user (id_keycloak, id_stare_cont, id_rol, nume, prenume, mail, facultate)
SELECT '<UUID_KEYCLOAK>',
       (SELECT id FROM stare_cont WHERE denumire = 'ACTIV'),
       (SELECT id FROM rol WHERE denumire = 'ADMIN'),
       'Admin', 'Principal', '<EMAIL_ADMIN>', NULL
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE mail = '<EMAIL_ADMIN>');
```

✅ **Verificare Etapa 1**: aplicația pornește, `ROL` are 3 rânduri, `STARE_CONT` are 5. După rularea §1.7, `APP_USER` are un rând `ACTIV`/`ADMIN`.

---

## Etapa 2 — 🤖 AGENT: Spring Security (BFF, CSRF, roluri, redirect register)

Implementează `GrantedAuthoritiesMapper`. Extrage UUID-ul (`sub`) din `OidcUserAuthority`, caută utilizatorul corespunzător în baza de date locală (`app_user`) și îi atribuie autoritatea Spring Security prefixată cu `ROLE_` (de ex: `ROLE_STUDENT` sau `ROLE_PROFESOR`). În cazul în care starea contului este `INCOMPLET` (utilizatorul nou înregistrat nu are încă rol), returnează o listă goală de autorități fără a bloca autentificarea.
```java
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
```

### 2.2 `CustomAuthorizationRequestResolver.java`

⚠️ Piesă nouă, esențială pentru fluxul de register. Implementează `OAuth2AuthorizationRequestResolver`:

Adăugat explicit PKCE `S256` prin `setAuthorizationRequestCustomizer` (Keycloak o cere pe clientul nostru).

```java
public class CustomAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
    private final OAuth2AuthorizationRequestResolver defaultResolver;

    public CustomAuthorizationRequestResolver(ClientRegistrationRepository repo) {
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers.withPkce());
        this.defaultResolver = resolver;
    }

    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return customize(defaultResolver.resolve(request), request);
    }

    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        return customize(defaultResolver.resolve(request, registrationId), request);
    }

    private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest req, HttpServletRequest request) {
        if (req == null) return null;
        if (request.getRequestURI().endsWith("/keycloak-register")) {
            String registerUri = req.getAuthorizationUri().replace("/auth", "/registrations");
            return OAuth2AuthorizationRequest.from(req).authorizationRequestUri(registerUri).build();
        }
        return req;
    }
}
```
Efect: `GET /oauth2/authorization/keycloak` → pagina normală de login Keycloak. `GET /oauth2/authorization/keycloak-register` → aceeași configurație de client, dar redirecționează spre `/protocol/openid-connect/registrations` (endpoint-ul Keycloak dedicat direct paginii de register, cu tema voastră custom de la Etapa 0.9) în loc de `/auth`.

### 2.3 `CsrfCookieFilter.java`
Acest filtru forțează rezolvarea token-ului CSRF "leneș" (deferred) la fiecare request, scriind cookie-ul `XSRF-TOKEN` în răspunsul HTTP, asigurându-se astfel că frontend-ul îl primește și îl poate trimite înapoi la POST/PUT request-uri:
```java
public class CsrfCookieFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        if (csrfToken != null) {
            csrfToken.getToken(); // Apelul getToken() forțează rezolvarea și scrie cookie-ul XSRF-TOKEN
        }
        filterChain.doFilter(request, response);
    }
}
```

### 2.4 `SecurityConfig.java`
- `.oauth2Login(oauth2 -> oauth2
    .authorizationEndpoint(ep -> ep.authorizationRequestResolver(new CustomAuthorizationRequestResolver(clientRegistrationRepository)))
    .userInfoEndpoint(ui -> ui.userAuthoritiesMapper(customAuthoritiesMapper()))
    .successHandler(customAuthenticationSuccessHandler())  -- vezi Etapa 3.1, logica de redirect diferențiat
  )`
- CSRF, `permitAll` pe `/error`/health-check, restul autenticat, logout cu `OidcClientInitiatedLogoutSuccessHandler`

### 2.5 `CorsConfig.java`
Configurare CORS pentru a permite apelurile venite din frontend-ul React local, inclusiv transmiterea cookie-urilor de sesiune și header-ului CSRF:
```java
@Configuration
public class CorsConfig {
    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(List.of(frontendBaseUrl));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Authorization"));
        config.setExposedHeaders(List.of("Location"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

✅ **Verificare Etapa 2**: `http://localhost:8081/oauth2/authorization/keycloak` → login Keycloak normal. `http://localhost:8081/oauth2/authorization/keycloak-register` → pagina de **register** Keycloak (cu tema de la 0.9, dacă e gata; altfel aspectul implicit, dar funcțional).

---

## Etapa 3 — 🤖 AGENT: Bootstrap USER la primul login/register + redirect diferențiat

### 3.1 `CustomAuthenticationSuccessHandler.java`

Implementează `AuthenticationSuccessHandler`, înlocuiește handler-ul simplu din rundele anterioare. La orice autentificare reușită (login SAU register — Keycloak le tratează identic din perspectiva callback-ului OAuth2):

1. Extrage `sub` din `Authentication` (`OidcUser`)
2. `SELECT` `User` după `ID_KEYCLOAK = sub`
3. **Dacă NU există** → primul login după register: `INSERT` rând nou — `ID_KEYCLOAK = sub`, `MAIL` = extras din claim-urile OIDC (`email`), `STARE_CONT = INCOMPLET`, restul câmpurilor `NULL` → redirecționează către `{frontend}/complete-profile`
4. **Dacă există**, redirecționează în funcție de `STARE_CONT`:
   - `INCOMPLET` → `{frontend}/complete-profile` (a început, dar n-a terminat data trecută)
   - `PENDING` → `{frontend}/asteptare-aprobare`
   - `RESPINS` → `{frontend}/cerere-respinsa`
   - `INACTIV` → `{frontend}/cont-dezactivat`
   - `ACTIV` → `{frontend}/` (aplicația normală)

### 3.2 `StareContFilter.java` — actualizat

Regulă nouă, mai nuanțată decât înainte (nu mai e doar "blochează dacă nu ACTIV"):
1. Neautentificat → trece mai departe
2. Autentificat, `User` inexistent în DB → **teoretic nu ar trebui să se întâmple** (handler-ul de la 3.1 creează rândul la primul callback) — dacă totuși apare, tratează ca eroare, `403`
3. Autentificat, `STARE_CONT = INCOMPLET` → permite **doar** `POST /api/auth/complete-profile` și `GET /api/auth/me`; orice alt endpoint → `403`
4. Autentificat, `STARE_CONT = PENDING` → permite doar `GET /api/auth/me`; restul → `403`
5. Autentificat, `STARE_CONT = RESPINS` → permite `GET /api/auth/me` și `POST /api/auth/complete-profile` (resubmisie, vezi Etapa 4); restul → `403`
6. Autentificat, `STARE_CONT = INACTIV` → doar `GET /api/auth/me`; restul → `403`
7. Autentificat, `STARE_CONT = ACTIV` → trece liber (comportamentul de dinainte)
8. Excepții by-pass la filtru pentru rutele `/error`, `/actuator/**` și `POST /logout`.

### 3.3 `MeController.java` — `GET /api/auth/me`
Returnează datele de identificare și starea contului utilizatorului curent din baza de date locală pe baza principalului `OidcUser`:
```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class MeController {
    private final UserRepository userRepository;

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
```

✅ **Verificare Etapa 3**: register nou prin `/oauth2/authorization/keycloak-register` → după completare pe pagina Keycloak, ajungi automat autentificat, cu un rând `APP_USER` nou (`STARE_CONT = INCOMPLET`) creat în DB.

---

## Etapa 4 — 🤖 AGENT: Complete-Profile

### 4.1 `CompleteProfileRequestDto.java`
`nume`, `prenume`, `facultate`, `rolDorit` (validat: `PROFESOR` sau `STUDENT`, NU `ADMIN`)

### 4.2 `CompleteProfileService.java`
Metodă `completeaza(String subKeycloak, CompleteProfileRequestDto dto)`:
1. `SELECT User WHERE ID_KEYCLOAK = subKeycloak`
2. `UPDATE` cu `UserMapper` (MapStruct): mapare valori din DTO către Entitate, plus setare `ID_STARE_CONT = PENDING`
3. Dacă starea anterioară era `RESPINS`, incrementează `NR_RESPINGERI` **nu aici** (a fost deja incrementat la momentul respingerii, vezi Etapa 5) — aici doar resetează la `PENDING`

### 4.3 `AuthController.java` (extins)
`POST /api/auth/complete-profile` — protejat prin autentificare (nu `permitAll`), accesibil datorită §3.2 pasul 3/5 indiferent de rol (userul încă nu are rol). Extrage `sub` din `Authentication`, apelează serviciul.

✅ **Verificare Etapa 4**: completând formularul, `STARE_CONT` devine `PENDING`, câmpurile se populează.

---

## Etapa 5 — 🤖 AGENT: Admin — accept/respinge (simplificat major, KISS — Keycloak nu mai stochează rol)

### 5.1 `KeycloakAdminService.java`

Dezactivează și reactivează utilizatorii direct în consola Keycloak via Admin API folosind apeluri PUT cu Service Account Client credentials:
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakAdminService {
    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final RestClient.Builder restClientBuilder;

    @Value("${app.keycloak.base-url}")
    private String keycloakBaseUrl;

    @Value("${app.keycloak.realm}")
    private String realm;

    public void dezactiveazaUser(String idKeycloak) {
        updateEnabled(idKeycloak, false);
    }

    public void reactiveazaUser(String idKeycloak) {
        updateEnabled(idKeycloak, true);
    }

    private void updateEnabled(String idKeycloak, boolean enabled) {
        try {
            restClient().put()
                    .uri(keycloakBaseUrl + "/admin/realms/" + realm + "/users/" + idKeycloak)
                    .header("Authorization", "Bearer " + getAdminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("enabled", enabled))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Keycloak: Contul utilizatorului sub={} a fost setat enabled={}", idKeycloak, enabled);
        } catch (RestClientException e) {
            throw new KeycloakIntegrationException(
                    "Eroare Keycloak la setarea enabled=" + enabled + " pentru sub=" + idKeycloak + ": " + e.getMessage(), e);
        }
    }

    private String getAdminToken() {
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId("keycloak-admin")
                .principal("service-account-keycloak-admin")
                .build();
        OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(authorizeRequest);
        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            throw new KeycloakIntegrationException(
                    "Nu s-a putut obține token-ul de service-account pentru clientul 'keycloak-admin'");
        }
        return authorizedClient.getAccessToken().getTokenValue();
    }

    private RestClient restClient() {
        return restClientBuilder.build();
    }
}
```

### 5.2 `AdminUserService.java`
- `listaCereriPending()`: `SELECT WHERE STARE_CONT = PENDING`, include `NR_RESPINGERI` per rând
- `acceptaUser(Long userId)`: ⚠️ **devine operație DB pură, fără niciun apel Keycloak**:
  1. `User` cu `STARE_CONT = PENDING` (altfel `400`)
  2. `STARE_CONT = ACTIV`

  Atât. Rolul e deja în DB din Etapa 4 (Complete-Profile) — nu mai e nimic de "transmis" către Keycloak.
- `respingeUser(Long userId)`: `STARE_CONT = RESPINS`, `NR_RESPINGERI += 1`. Keycloak **neatins**.
- `dezactiveazaUser(Long userId)`: Schimbă starea locală a contului în `INACTIV`, face apel către `KeycloakAdminService` pentru a dezactiva contul la nivelul Keycloak, prevenind logări viitoare.
- `activeazaUser(Long userId)`: Schimbă starea locală în `ACTIV` și reactivează contul în Keycloak.

### 5.3 `AdminController.java`
Controller protejat la nivel de clasă cu securitatea `@PreAuthorize("hasRole('ADMIN')")`:
```java
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AdminUserService adminUserService;

    @GetMapping("/users")
    public List<UserPendingDto> listaUseri(@RequestParam(defaultValue = "PENDING") String stare) {
        return adminUserService.listaCereriPending();
    }

    @PostMapping("/users/{id}/accept")
    public void acceptaUser(@PathVariable Long id) {
        adminUserService.acceptaUser(id);
    }

    @PostMapping("/users/{id}/reject")
    public void respingeUser(@PathVariable Long id) {
        adminUserService.respingeUser(id);
    }

    @PostMapping("/users/{id}/deactivate")
    public void dezactiveazaUser(@PathVariable Long id) {
        adminUserService.dezactiveazaUser(id);
    }

    @PostMapping("/users/{id}/activate")
    public void activeazaUser(@PathVariable Long id) {
        adminUserService.activeazaUser(id);
    }
}
```

✅ **Verificare Etapa 5**: accept → `STARE_CONT = ACTIV` (verifică doar în DB — Keycloak Users nu arată niciun rol, e normal, nu mai stochează asta). Respinge → `STARE_CONT = RESPINS`, userul tot există și poate loga în Keycloak (dar blocat de filtru, vezi Etapa 3.2).

---
## Etapa 8 — 🤖 AGENT: Date demo pentru evaluare (opțional, profil separat)

Similar cu runda anterioară, dar acum simulează stadiul realist pentru admin: `STARE_CONT = PENDING`, cu `NUME`/`PRENUME`/`FACULTATE`/`ID_ROL` deja completate (ca și cum ar fi trecut deja prin Complete-Profile), `ID_KEYCLOAK` = UUID-uri fictive generate random (nu userii chiar nu există în Keycloak — nu contează, fiindcă demo-ul testează doar panoul admin, nu login-ul acelor conturi). `@Profile("demo")`, dezactivat implicit.

---
