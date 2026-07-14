# Akadion Backend — Ghid de Setup și Documentație API (Flux Înregistrare Nativă Keycloak)

Acest repository conține backend-ul aplicației **Akadion** (dezvoltată în Spring Boot 3.x și Java 21), configurată după modelul **BFF (Backend-for-Frontend)** cu integrare **Keycloak OAuth2** (înregistrare nativă + flux de completare profil) și baza de date relațională **PostgreSQL**.

> [!NOTE]
> Frontend-ul React (Vite) este gestionat separat de un alt membru al echipei. Acest ghid documentează exclusiv structura, setup-ul local și interfețele expuse de backend.

---

## 🛠️ Setup Local pentru Evaluare

Urmează pașii de mai jos în ordine pentru a porni și configura mediul backend local.

### 1. Pornire Infrastructură (Docker Compose)
Dacă folosești Docker, pornește baza de date PostgreSQL și Keycloak folosind:
```bash
docker compose up -d
```
*Notă: Dacă nu folosești Docker, asigură-te că ai o instanță de PostgreSQL pornită local pe portul `5432` cu baza de date numită `akadion`, iar Keycloak rulează local pe portul `8080`.*

### 2. Configurare Manuală Keycloak
Accesează consola Keycloak la `http://localhost:8080` și configurează următoarele:
1. **Creare Realm**: Creează un realm cu denumirea `akadion`.
2. **Auto-înregistrare (Login tab)**:
   - Setează `User registration` = **ON**.
   - Setează `Email as username` = **ON**.
3. **User Profile (User profile tab)**:
   - Păstrează doar `email` ca atribut obligatoriu.
   - Șterge `firstName` și `lastName` pentru a asigura o înregistrare minimă (email + parolă).
4. **Creare Clienți**:
   - **`backend-login`**:
     - Client Type: `OpenID Connect`
     - Client Authentication: `ON` (Confidențial)
     - Authorization Code Flow: `ON`
     - PKCE Method: `S256`
     - Valid Redirect URIs: `http://localhost:8081/login/oauth2/code/keycloak` și `http://localhost:5173/*`
     - Web Origins: `http://localhost:8081`
     - Tab Credentials -> copiază `Client secret`
   - **`backend-admin-api`**:
     - Client Type: `OpenID Connect`
     - Client Authentication: `ON` (Confidențial)
     - Service Accounts Roles: `ON`
     - Tab Service accounts roles -> Assign role -> din `realm-management` -> bifează `manage-users` și `view-users`
     - Tab Credentials -> copiază `Client secret`
5. **Utilizator Admin de Bootstrap**: Creează manual un prim cont de admin direct în Keycloak (ex. `admin@akadion.ro`), asociază-i o parolă (debifează Temporary) și reține ID-ul său Keycloak (UUID).

### 3. Configurare Secrete local (application-local.properties)
Copiaza fișierul `application-local.properties.example` în `application-local.properties` (fișierul real este ignorat în git) și completează secretele tale:
```properties
spring.datasource.password=parola_ta_db
spring.security.oauth2.client.registration.keycloak.client-secret=secretul_pentru_backend_login
spring.security.oauth2.client.registration.keycloak-admin.client-secret=secretul_pentru_backend_admin_api
```
Sau setează direct variabilele de mediu: `DB_PASSWORD`, `KEYCLOAK_BACKEND_LOGIN_SECRET` și `KEYCLOAK_ADMIN_API_SECRET`.

### 4. Bootstrap Admin în Baza de Date
Inserează manual rândul pentru admin-ul creat la pasul 2 direct în tabelul `app_user` din baza de date `akadion` (folosind `bootstrap-admin.sql` adaptat):
```sql
INSERT INTO app_user (id_keycloak, id_stare_cont, id_rol, nume, prenume, mail, facultate)
VALUES (
    'UUID-ul-utilizatorului-din-Keycloak',
    (SELECT id FROM stari_cont WHERE denumire = 'ACTIV'),
    (SELECT id FROM roluri WHERE denumire = 'ADMIN'),
    'Admin',
    'Principal',
    'admin@akadion.ro',
    NULL
);
```

### 5. Pornire Backend
Rulează compilarea și pornește aplicația:
```bash
mvn spring-boot:run
```
Pentru a popula baza de date cu **~10 cereri de înregistrare PENDING** (care simulează profilul deja completat) de test, pornește aplicația activând profilul `demo`:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

---

## 🔒 Arhitectură Securitate & Fluxuri BFF (Noul Flux)

1. **Register**: Frontend redirecționează către `/oauth2/authorization/keycloak-register`. Utilizatorul completează email + parolă direct în Keycloak (parola nu atinge backend-ul aplicației).
2. **Prima Autentificare**: Keycloak redirecționează către callback-ul backend-ului.
   - Backend detectează că nu există o înregistrare în DB locală cu acel `id_keycloak` (`sub`).
   - Inserează un rând schelet în tabela `app_user` cu `id_keycloak`, `mail` (extras din token), `stare_cont` = `INCOMPLET`, restul câmpurilor fiind `NULL`.
   - Redirecționează utilizatorul la `{frontend}/complete-profile`.
3. **Completare Profil**: Utilizatorul introduce numele, prenumele, facultatea și rolul dorit (STUDENT / PROFESOR), trimițând un request POST la `/api/auth/complete-profile`.
   - Backend actualizează datele în DB și setează starea contului pe `PENDING`.
   - Redirecționează utilizatorul la `{frontend}/asteptare-aprobare`.
4. **Aprobare Admin**:
   - Adminul vizualizează cererile (`GET /api/admin/users?stare=PENDING`).
   - Adminul acceptă (`POST /api/admin/users/{id}/accept`) -> starea devine `ACTIV` în DB. Rolul este deja stocat în DB. Keycloak nu se atinge.
   - Adminul respinge (`POST /api/admin/users/{id}/reject`) -> starea devine `RESPINS`, contorul `nr_respingeri` se incrementează. Utilizatorul se poate loga din nou în Keycloak și are posibilitatea să editeze profilul pentru a retrimite cererea (starea revine la `PENDING`).

---

## 📡 API Endpoints

### Endpoint-uri Utilizatori Autentificați (BFF Session Cookie + CSRF required)
- `GET /api/auth/me` — Returnează datele utilizatorului curent și starea contului.
  - **Response (200 OK)**:
    ```json
    {
      "id": 12,
      "nume": "Ionescu",
      "prenume": "Maria",
      "mail": "maria@student.test",
      "rol": "STUDENT", // poate fi null dacă starea e INCOMPLET
      "stareCont": "PENDING" // INCOMPLET, PENDING, ACTIV, RESPINS, INACTIV
    }
    ```
- `POST /api/auth/complete-profile` — Salvează detaliile profilului (nume, prenume, facultate, rolDorit). Permis doar în stările `INCOMPLET` și `RESPINS`.
  - **Body**:
    ```json
    {
      "nume": "Ionescu",
      "prenume": "Maria",
      "facultate": "Matematică",
      "rolDorit": "STUDENT" // STUDENT sau PROFESOR
    }
    ```
- `POST /logout` — Delogare din backend și Keycloak.

### Endpoint-uri Administrator (Rol `ADMIN` cerut în DB local)
- `GET /api/admin/users?stare=PENDING` — Listează cererile pending de aprobare (include `nrRespingeri` pentru a semnala resubmisiile).
- `POST /api/admin/users/{id}/accept` — Aprobă cererea (`stareCont` devine `ACTIV`).
- `POST /api/admin/users/{id}/reject` — Respinge cererea (`stareCont` devine `RESPINS`, `nr_respingeri` se incrementează).
- `POST /api/admin/users/{id}/deactivate` — Dezactivează utilizatorul (`stareCont` devine `INACTIV` și contul Keycloak este dezactivat).
