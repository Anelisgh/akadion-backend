# Infrastructură și Testare (Auth)

## Anexa A — Docker Compose

Pentru rularea locală completă într-un mod unitar și reproductibil, fișierul `compose.yaml` (sau `docker-compose.yml`) trebuie să conțină atât serviciul de bază de date PostgreSQL, cât și serviciul Keycloak gata configurat:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16
    container_name: postgres-akadion
    environment:
      POSTGRES_DB: akadion
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

  keycloak:
    image: quay.io/keycloak/keycloak:24.0
    container_name: keycloak-akadion
    command: start-dev
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/akadion
      KC_DB_USERNAME: postgres
      KC_DB_PASSWORD: ${DB_PASSWORD}
    ports:
      - "8080:8080"
    depends_on:
      - postgres
    volumes:
      # Tema custom (Etapa 0.9) este montată ca volum în containerul Keycloak, altfel dispare la trecerea de la Keycloak local la Docker.
      - ./themes/akadion-theme:/opt/keycloak/themes/akadion-theme

volumes:
  pgdata:
```

---

## Anexa B — Variabile de mediu

Pentru configurarea corectă a rulării proiectului (atât local în IDE, cât și în containere Docker / medii de staging sau producție), este necesar să definim următoarele variabile de mediu:

| Variabilă | Rol / Destinație | Valoare de exemplu (Local) |
|---|---|---|
| `DB_PASSWORD` | Parola de conectare la baza de date PostgreSQL. | `postgres` / `secret123` |
| `KEYCLOAK_BACKEND_LOGIN_SECRET` | Client Secret-ul obținut din Keycloak pentru clientul `backend-login` (BFF Auth Flow). | *generat în tab-ul Credentials* |
| `KEYCLOAK_ADMIN_API_SECRET` | Client Secret-ul obținut din Keycloak pentru clientul `backend-admin-api` (Admin REST API). | *generat în tab-ul Credentials* |
| `SPRING_PROFILES_ACTIVE` | *(Opțional)* Profilele active Spring Boot. Folosește `demo` pentru seeding automat. | `demo` |

> [!TIP]
> În dezvoltare locală, poți crea un fișier numit `application-local.properties` în `proiect/src/main/resources/` (acesta este ignorat de Git) pentru a adăuga aceste variabile direct sub formă de proprietăți Spring, de exemplu:
> ```properties
> spring.datasource.password=postgres
> spring.security.oauth2.client.registration.keycloak-register.client-secret=secret_aici
> spring.security.oauth2.client.registration.keycloak-admin.client-secret=secret_aici
> ```

---

## Anexa C — Note pentru producție

Trecerea de la rularea locală (`start-dev` pe Keycloak, `ddl-auto=update` pe Spring) la un mediu real de producție implică următoarele măsuri obligatorii de securitate și stabilitate:

### 1. Securizarea Comunicației (HTTPS/SSL)
* **SSL obligatoriu**: Atât Keycloak (în modul producție), cât și serverul de backend și frontend-ul React trebuie să ruleze exclusiv în spatele unui proxy securizat (ex. Nginx, Traefik) cu certificate SSL valide (Let's Encrypt / Cloudflare).
* **Cookie-uri securizate**: În `application.properties`, asigurați-vă că proprietățile de sesiune sunt configurate corect pentru HTTPS:
  ```properties
  server.servlet.session.cookie.secure=true
  server.servlet.session.cookie.http-only=true
  ```

### 2. Configurația Keycloak de Producție
* **Modul Producție**: Pornirea se face folosind comanda `kc.sh start` în loc de `kc.sh start-dev`, cu configurarea corectă a bazei de date de producție din Keycloak.
* **Redirect URI Securizat**: În setările clienților din Keycloak, eliminați redirectările de tip wildcard (`http://localhost:*`, `http://*` sau `*`) și adăugați exact adresele URL de producție securizate (ex: `https://akadion.ro/login/oauth2/code/keycloak`).

### 3. Gestionarea Bazei de Date (Spring Boot)
* **Dezactivare DDL-Auto**: Dezactivați proprietatea hibernate ddl-auto în producție sau setați-o pe `validate`:
  ```properties
  spring.jpa.hibernate.ddl-auto=validate
  ```
* **Activarea Flyway**: Decomentați proprietatea `# spring.flyway.enabled=true` și utilizați migrații SQL versionate sub folderul `db/migration/` pentru orice modificare adusă schemei bazei de date.

### 4. Scalabilitate și Sesiuni (BFF)
* Deoarece arhitectura BFF folosește sesiuni in-memory implicit, dacă backend-ul se scalează pe orizontală (multiple instanțe în spatele unui Load Balancer), trebuie fie să folosiți **Sticky Sessions** la nivelul proxy-ului, fie să configurați **Spring Session cu Redis** (`spring-session-data-redis`) ca magazin centralizat de sesiuni.

---

## Anexa D — README.md pentru evaluator

Actualizat cu pasul nou de temă:
1. `docker-compose up -d`
2. Config manuală Keycloak (Etapa 0 — include acum și activarea `User registration`)
3. Temă custom (Etapa 0.9) — sau sări peste, tema implicită Keycloak funcționează, doar arată urât
4. Secrete (`application-local.yml`)
5. `bootstrap-admin.sql` (Etapa 1.7)
6. Pornire backend + frontend
7. Test: `/register` de pe frontend, parcurge fluxul complet