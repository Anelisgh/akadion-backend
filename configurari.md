# Documentație: Arhitectură și Configurări (Pachetul `com.example.akadion.config`)

Acest document acoperă setările fundamentale, arhitectura de boot și configurările din pachetul `com.example.akadion.config`.

## 1. Arhitectura de Baza
Aplicația este un monolit modular construit în Spring Boot 3. Conține o arhitectură stratificată (Layered Architecture):
- **Controllers** (`/controller`): Tratează cererile HTTP și validează DTO-urile.
- **Services** (`/service`): Conțin regulile de business.
- **Repositories** (`/repository`): Extind `JpaRepository` pentru interogări DB.
- **Entities** (`/entity`): Mapare JPA (Hibernate).
- **Security** (`/security`): Filtre și Mappers specifice OAuth2.
- **Config** (`/config`): Bean-uri de configurare globală.

## 2. Configurări Core (`/config`)

### 2.1. AuditConfig
- **Scop:** Activează `Spring Data JPA Auditing` (`@EnableJpaAuditing`).
- **Implementare:** Definește un `AuditorAware<String>` care citește `SecurityContextHolder`.
- **Logica de determinare autor:** 
  1. Dacă e un `OidcUser` (login din browser), returnează UUID-ul (`sub`).
  2. Dacă e un `Jwt` (login machine-to-machine), returnează claim-ul `sub`.
  3. Altfel, încearcă cast la `String`.
  4. Fallback absolut: Returnează `'system'`. Acest lucru e esențial pentru faza de seed (când serverul pornește, nu există un utilizator autentificat în firul de execuție, așadar conturile create automat primesc 'system' ca autor).

### 2.2. CorsConfig
- **Scop:** Permite comunicarea cu frontend-ul (React) rulat pe alt port.
- **Implementare:** Implementează `WebMvcConfigurer`. Permite explicit `app.frontend.base-url` (din `application.properties`), metodele `GET, POST, PUT, PATCH, DELETE, OPTIONS`.
- Permite credentials (`allowCredentials(true)`) necesare pentru setarea sesiunii JSESSIONID și a cookie-ului `XSRF-TOKEN`.

### 2.3. DataSeeder
- **Scop:** Populează nomenclatoarele de bază la startul aplicației.
- **Implementare:** Implementează `CommandLineRunner`. La metoda `run`, execută inserții folosind repositoriile:
  - Dacă tabela `roluri` e goală: inserează `ADMIN`, `PROFESOR`, `STUDENT`.
  - Dacă tabela `stari_cont` e goală: inserează `INCOMPLET`, `PENDING`, `ACTIV`, `INACTIV`, `RESPINS`.
- *Dezvoltat astfel încât aplicația să nu necesite migrații Flyway manuale doar pentru a fi rulabilă (totuși aplicația este pregătită cu migrații în `/db/migration/`).*

### 2.4. MinioConfig
- **Scop:** Expune clientul necesar conectării la serverul de S3/Object Storage (MinIO).
- **Variabile:** `minio.url`, `minio.access-key`, `minio.secret-key`, `minio.bucket`.
- **Implementare:** Construiește un `MinioClient`. Dispune de flag-ul `minio.auto-create-bucket` care creează bucket-ul pe disc dacă nu există (cu logica corespunzătoare `bucketExists` și `makeBucket`).

### 2.5. OAuth2ClientConfig
- **Scop:** Backend-ul trebuie să facă request-uri la Keycloak pentru a da Ban (dezactivare cont) sau Reset Parolă unui utilizator.
- **Implementare:** Backend-ul are el însuși nevoie de un token. Folosește un client `OAuth2AuthorizedClientManager` configurat explicit pentru `client-credentials` (comunicare server-to-server invizibilă pentru user). Se definește `AuthorizedClientServiceOAuth2AuthorizedClientManager` pentru a menține token-ul în memorie.

### 2.6. SecurityConfig
- **Scop:** Interceptează request-urile. Definește logica OIDC.
- Aici se atașează `CustomAuthenticationSuccessHandler`, `CustomAuthoritiesMapper`, filtrele `CsrfCookieFilter`, `StareContFilter`, `RequestIdFilter` și `AccessLogFilter`.

### 2.7. RequestIdFilter
- **Scop:** Adaugă un identificator unic (`X-Request-ID`) fiecărui request HTTP pentru trasabilitate și debug.
- **Implementare:** Este un `OncePerRequestFilter` cu `@Order(Ordered.HIGHEST_PRECEDENCE)`. Preia header-ul `X-Request-ID` sau generează un UUID nou, îl pune în `MDC` (Mapped Diagnostic Context) pentru a apărea automat în log-uri, și îl atașează la response.

### 2.8. AccessLogFilter
- **Scop:** Jurnalizează toate request-urile HTTP primite (metodă, path, status code, durată de execuție, utilizator).
- **Implementare:** Este un `OncePerRequestFilter` cu `@Order(Ordered.LOWEST_PRECEDENCE)`. Extrage utilizatorul curent din `SecurityContextHolder` și utilizează Logstash-Logback pentru log-uri structurate (`kv("duration_ms", ...)`).

### 2.9. RagClientConfig
- **Scop:** Configurează clienții HTTP (`RestClient`) pentru comunicarea cu serviciul extern RAG.
- **Implementare:** Expune două bean-uri de `RestClient`: `ragChatRestClient` și `ragEmbedderRestClient`. Configurează **Basic Authentication** și interceptează cererile (`ClientHttpRequestInterceptor`) pentru a propaga antetele `X-Request-ID` și `X-User` (utilizatorul curent).

## 3. Aplicația - Punct de Intrare (`AkadionApplication`)
- Simplă clasă Spring Boot ce inițiază serverul Tomcat.
- Proprietățile principale sunt externalizate în `application.properties` sau `application-local.properties` (baza de date PostgreSQL, Keycloak issuer URL, limite fisier multipart 50MB, adresa FastAPI `app.rag.base-url`).
