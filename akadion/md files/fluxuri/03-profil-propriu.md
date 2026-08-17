# Flux 3: Profil Propriu (Editare, Schimbare Email, Resetare Parolă)

> Actor: **orice utilizator autentificat** (indiferent de rol/stare — vezi excepția de mai jos).
> Referințe tehnice complete: `../services.md` §10, `../api-controllers.md` §5, `../business-rules.md` §6.

---

## De ce acest flux e "special" față de restul aplicației

`MeController` (`/api/auth/me`) e singurul controller la care au acces conturile în **orice** stare — inclusiv `PENDING` și `INACTIV` — pentru că un utilizator trebuie mereu să poată vedea cine e și ce stare are (chiar dacă blocat), ca frontend-ul să știe ce ecran să-i arate. E și motivul pentru care `MeController` e explicit exceptat de la `@CurrentUser` (rezolvatorul de parametru folosit peste tot altundeva) — ar fi necesitat refactorizarea semnăturilor din `UserProfileService`, și a fost lăsat intenționat diferit.

---

## Editare profil (nume, prenume, facultate)

**Ce se întâmplă:** Utilizatorul modifică datele din formularul de profil și salvează.

**Cum:** `PUT /api/auth/me` cu `UpdateProfileRequestDto (nume, prenume, facultate)` → `UserProfileService` actualizează direct rândul din `app_user`. Nicio interacțiune cu Keycloak — aceste câmpuri nu există acolo, sunt strict locale.

---

## Schimbarea email-ului — de ce e tratată ca operațiune periculoasă

**Ce se întâmplă:** Utilizatorul introduce un email nou și salvează.

**De ce e complicat:** Aplicația are **două surse de adevăr pentru email** — Keycloak (folosit pentru login) și `app_user.mail` (folosit local, cu constrângere de unicitate). Cele două nu pot fi actualizate atomic (sunt sisteme diferite, fără tranzacție distribuită reală disponibilă). Dacă am actualiza doar unul, cele două ar diverge silențios — utilizatorul s-ar loga cu un email, dar aplicația ar afișa altul.

**Cum (tehnic) — model de tip Saga cu compensare:**
1. `PUT /api/auth/me/email` cu `UpdateEmailRequestDto (newEmail)`.
2. `UserProfileService` verifică întâi că noul email nu e deja folosit local (evită conflict înainte de a atinge Keycloak).
3. **Pas 1**: schimbă email-ul în Keycloak (`KeycloakAdminService`), și setează `emailVerified = false` acolo — utilizatorul va trebui să-l reconfirme (Keycloak trimite el însuși email-ul de verificare).
4. **Pas 2**: schimbă email-ul în DB locală cu `saveAndFlush` (forțează scrierea imediată, ca să prindă imediat eventuala violare a constrângerii unice, nu la următorul flush lazy).
5. **Compensare**: dacă pasul 2 eșuează (ex: race condition — altcineva a apucat să ia exact acel email între verificarea din pasul 0 și scriere), execuția intră pe un bloc `catch` care **revine** la Keycloak și pune la loc email-ul vechi — pentru ca cele două sisteme să nu rămână desincronizate.

**De ce contează ordinea:** Keycloak se schimbă *primul* pentru că e sistemul "greu" — dacă acela eșuează, nu s-a schimbat nimic încă local, deci nu trebuie compensat nimic. Dacă DB-ul local eșuează *după* ce Keycloak a reușit deja, avem nevoie explicit de acel pas de compensare — altfel utilizatorul s-ar loga cu emailul nou, dar aplicația ar căuta încă rândul cu emailul vechi.

---

## Resetare parolă

**Ce se întâmplă:** Utilizatorul apasă "Resetează parola" din profil (nu de pe ecranul de login — aici discutăm fluxul din interiorul aplicației).

**Cum:** `POST /api/auth/me/request-password-reset` → `UserProfileService` apelează `KeycloakAdminService`, care declanșează pe `sub`-ul curent o acțiune de tip `UPDATE_PASSWORD` prin Admin REST API-ul Keycloak (`execute-actions-email`). Keycloak trimite el însuși emailul cu link-ul de resetare — backend-ul Spring nu vede, nu procesează și nu stochează nicio informație despre parolă în niciun moment. Răspunsul e `202 Accepted` — acceptat pentru procesare, nu o confirmare că emailul a ajuns efectiv.

**De ce așa:** Consecvent cu principiul de bază al aplicației (flux 1) — parolele sunt strict responsabilitatea Keycloak. Reset-ul, ca și verificarea de email, folosește exact același mecanism (`execute-actions-email`) doar cu un tip de acțiune diferit (`UPDATE_PASSWORD` vs `VERIFY_EMAIL`).

---

## Diagramă (schimbare email)

```
PUT /api/auth/me/email {newEmail}
        │
        ▼
verifică duplicat local ── dacă există deja ──> ForbiddenOperationException (403) / EmailDuplicatException (409)
        │ ok
        ▼
Keycloak: schimbă email + emailVerified=false
        │
        ▼
DB locală: saveAndFlush(mail = newEmail)
        │
   ┌────┴────┐
 succes     eșec (constrângere unică / eroare DB)
   │           │
   ▼           ▼
  200      catch → Keycloak: revert la emailul vechi (compensare Saga)
```

---

## Ce poate merge prost

| Situație | Ce se întâmplă | Cod |
|---|---|---|
| Email nou deja folosit de alt cont local | Blocat înainte de a atinge Keycloak | 403/409 |
| Keycloak jos la schimbarea email-ului | `KeycloakIntegrationException`, nimic modificat local | 502 |
| DB local eșuează după ce Keycloak a reușit deja | Compensare automată — email-ul revine la vechea valoare în Keycloak | 500 (către client) + revert reușit în spate |
| Reset parolă cerut, dar Keycloak jos | `KeycloakIntegrationException` | 502 |
