# Task pentru agent AI: Implementare audit_log (Istoric Schimbări) — Pas 2

Acest task urmează raportului de evaluare deja aprobat. Toate deciziile de scope de mai jos sunt **finale** — nu le renegocia, nu le extinde tacit. Dacă găsești ceva neclar la un punct anume, întreabă explicit, nu presupune.

## Decizii confirmate (context, nu de discutat)

- **Scope:** 20 puncte de apel, pe 5 entități — `User`, `Curs`, `Saptamana`, `Document`, `UserCurs` (lista completă mai jos). `User` are acum 8 puncte, nu 4 — include și creare cont la prim login, editare profil, și schimbare email.
- **NU** se auditează `Parcurs` (bifele de progres ale studenților) — volum mare, valoare administrativă mică.
- **Schimbarea de email** (`UserProfileService.updateMyEmail`) SE include, cu o condiție specială — vezi Pas 0.7. Implementarea curentă a acestui flux e marcată de coordonator ca „de refăcut" mai robust; rămâne funcțională deocamdată, doar semnalată pentru rescriere ulterioară.
- **NU** se auditează `retry-ingest` pe documente — e reîncercare tehnică, nu schimbare de business.
- **Apeluri explicite** din servicii, nu adnotare custom + aspect AOP — decizie luată ținând cont de timpul rămas din proiect și de faptul că un singur dezvoltator întreține tot codul Spring.
- **`utilizator`**: `VARCHAR(36)` = `id_keycloak`, NU foreign key `BIGINT` către `app_user.id`. Motiv decisiv: acțiunile `"system"` nu au niciun rând în `app_user`, deci un FK real ar fi imposibil de respectat pentru acele cazuri; în plus, un audit trail trebuie să supraviețuiască independent de existența ulterioară a userului.
- **JSONB minimal** — doar câmpurile efectiv relevante pentru acțiune, NU serializare completă a entității.
- **`operatie`**: nume semantice de business (`APROBARE`, `RESPINGERE`, `DEZACTIVARE`, `ACTIVARE`, `CREARE`, `EDITARE`, `STERGERE`, `INSCRIERE`, `RETRAGERE`, `UPLOAD`, `INLOCUIRE`, `COMPLETARE_PROFIL`) — NU `INSERT`/`UPDATE`/`DELETE` generic.
- **Livrabil complet acest sprint:** backend (captare) + un endpoint + o pagină simplă de admin pentru vizualizare. Nu doar infrastructură de captare fără UI.

## Pas 0 — Verificări obligatorii înainte de cod

1. Confirmă numărul următor de migrare Flyway disponibil chiar înainte de a scrie migrarea (probabil `V8`, dar reverifică — altcineva din echipă poate fi creat între timp alt script).
2. Pentru fiecare din cele 20 metode țintă (lista de mai jos): verifică semnătura exactă și confirmă ce e disponibil ca "valoare veche" înainte de modificare — la `UPDATE`/dezactivări, ai nevoie de starea entității *înainte* de a aplica schimbarea, deci verifică dacă metoda deja încarcă entitatea din DB înainte de a o modifica (de regulă da), sau dacă trebuie adăugat explicit un pas de citire.
3. Pentru fiecare din cele 20, confirmă dacă metoda e `@Transactional` la nivel propriu sau moștenește tranzacția apelantului — apelul de audit trebuie să cadă **în aceeași tranzacție** cu schimbarea reală (commit/rollback împreună), niciodată separat.
4. Pentru `DocumentService` (punctele 16-18): NU duplica sau interfera cu logica deja implementată de validare Tika + hash anti-duplicat + ordinea față de MinIO stabilită în rundele anterioare. Apelul de audit se adaugă **după** ce operațiunea reală (upload/înlocuire/ștergere) a reușit complet, nu înainte, nu în paralel cu logica de rollback existentă pe erori MinIO/DB.
5. Verifică ce reutilizează deja din audit-ul simplu existent (batch `findByIdKeycloakIn` pentru afișarea numelor, nu id_keycloak brut, în UI) — pagina nouă de admin trebuie să folosească același tipar pentru afișarea utilizatorilor, nu un query nou per rând.
6. **Punctul 1 (creare cont la prim login)** se întâmplă în `CustomAuthenticationSuccessHandler`, nu într-un serviciu `@Transactional` obișnuit — verifică explicit dacă poți insera curat un apel de audit acolo, fără să strici fluxul de redirect OIDC. Dacă nu există un loc curat, raportează asta separat, nu forța o soluție fragilă doar ca să bifezi punctul.
7. **Punctul 8 (schimbare email)** se scrie în `audit_log` **doar dacă Saga reușește complet** (update Keycloak + update DB, ambele finalizate cu succes) — niciodată pe ramura de compensare/rollback, unde emailul revine la valoarea veche. Plasează apelul de audit izolat, într-un singur punct clar de succes al metodei, nu împrăștiat prin blocurile de compensare — implementarea urmează să fie rescrisă mai robust de altcineva/altcândva, iar un apel izolat e mult mai ușor de mutat intact odată cu rescrierea, față de unul îngropat în logica de saga.

## Lista completă a celor 20 puncte de apel

### User
| # | Metodă | `operatie` | Payload minimal sugerat |
|---|---|---|---|
| 1 | `CustomAuthenticationSuccessHandler` — creare cont la prim login | `CREARE_CONT` | nou: `{mail, stare: "INCOMPLET"}` — vezi Pas 0.6 pentru punctul de inserție |
| 2 | `CompleteProfileService.completeaza` | `COMPLETARE_PROFIL` | nou: `{rolDorit, facultate}` |
| 3 | `UserProfileService` — editare profil (`PUT /api/auth/me`) | `EDITARE_PROFIL` | vechi/nou: `{nume, prenume, facultate}` |
| 4 | `AdminUserService.approveUser` | `APROBARE` | `{stare: "PENDING"→"ACTIV"}` |
| 5 | `AdminUserService.rejectUser` | `RESPINGERE` | `{stare: "PENDING"→"RESPINS", nrRespingeri}` |
| 6 | `AdminUserService.executeLocalDeactivation` | `DEZACTIVARE` | `{stare: "ACTIV"→"INACTIV"}` |
| 7 | `AdminUserService.executeLocalReactivation` | `REACTIVARE` | `{stare: "INACTIV"→"ACTIV"}` |
| 8 | `UserProfileService.updateMyEmail` | `SCHIMBARE_EMAIL` | vechi/nou: `{mail}` — scris DOAR la succesul complet al Saga, vezi Pas 0.7 |

### Curs
| # | Metodă | `operatie` | Payload minimal sugerat |
|---|---|---|---|
| 6 | `CursService` — creare curs | `CREARE` | nou: `{denumire, dataInceput}` |
| 7 | `CursService` — editare curs | `EDITARE` | vechi/nou: `{denumire, descriere, dataInceput}` |
| 8 | `CursService.dezactiveazaCurs` | `DEZACTIVARE` | `{activ: true→false}` |
| 9 | `CursService.activeazaCurs` | `ACTIVARE` | `{activ: false→true}` |

### Saptamana
| # | Metodă | `operatie` | Payload minimal sugerat |
|---|---|---|---|
| 10 | `SaptamanaService` — creare săptămână | `CREARE` | nou: `{nrSaptamana, descriere}` |
| 11 | `SaptamanaService` — editare descriere | `EDITARE` | vechi/nou: `{descriere}` |
| 12 | `SaptamanaService.stergeUltimaSaptamana` | `STERGERE` | vechi: sumar `{nrSaptamana, descriere, nrDocumenteAsociate}` — NU enumerare completă a documentelor/parcursurilor șterse în cascadă |

### Document
| # | Metodă | `operatie` | Payload minimal sugerat |
|---|---|---|---|
| 13 | `DocumentService.adaugaDocument` | `UPLOAD` | nou: `{titlu, pathMinio}` |
| 14 | `DocumentService.modificaDocument` | `INLOCUIRE` | vechi/nou: `{titlu, pathMinio}` |
| 15 | `DocumentService` — soft-delete | `STERGERE` | vechi: `{titlu, pathMinio}` |

### UserCurs
| # | Metodă | `operatie` | Payload minimal sugerat |
|---|---|---|---|
| 16 | `StudentCursService.inscriereCurs` | `INSCRIERE` | nou: `{cursId, activ: true}` |
| 17 | `StudentCursService` — retragere | `RETRAGERE` | `{activ: true→false}` |

## Schema DB (migrare V8)

```sql
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    nume_tabel VARCHAR(50) NOT NULL,
    id_inregistrare BIGINT NOT NULL,
    operatie VARCHAR(30) NOT NULL,
    utilizator VARCHAR(36),
    valori_vechi JSONB,
    valori_noi JSONB,
    creat_la TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_tabel_inregistrare ON audit_log (nume_tabel, id_inregistrare);
CREATE INDEX idx_audit_log_creat_la ON audit_log (creat_la DESC);
```

*(Al doilea index e nou față de propunerea inițială — pagina de admin cel mai probabil afișează cronologic descrescător, deci merită indexat direct pe asta.)*

## Backend

- `AuditLogService` cu o metodă unică, reutilizată din toate cele 17 locuri: `inregistreaza(String numeTabel, Long idInregistrare, String operatie, Object valoriVechi, Object valoriNoi)`.
- Extrage utilizatorul curent prin aceeași sursă folosită deja de `AuditorAware<String>` din `AuditConfig` — nu reimplementa logica de extragere din `SecurityContextHolder`.
- Endpoint nou, doar admin: `GET /api/admin/audit-log`, cu paginare (`Slice<T>`, offset-based, la fel ca la lista de conversații — nu keyset, nu e nevoie de scroll infinit aici).

## Frontend

- Pagină nouă simplă, în stilul `AdminUsersPage` (tabel, nu carduri) — o listă cronologică, paginată, cu coloane: tabel afectat, operație, utilizator (nume, nu id_keycloak brut — reutilizează tiparul de batch lookup din audit-ul simplu), dată.
- Opțional, dacă timpul permite: click pe un rând expandează `valori_vechi`/`valori_noi` (JSON brut e acceptabil pentru MVP, nu ai nevoie de un diff vizual sofisticat).
- Rută nouă protejată prin `RequireAdmin`, consistent cu `/admin/users`.

## Ce să NU faci

- Nu adăuga `Parcurs`, schimbarea de email, sau `retry-ingest` în listă, oricât de tentant ar părea "cât timp tot suntem aici".
- Nu introduce AOP sau adnotări custom.
- Nu sparge audit-ul simplu existent (`BaseAuditableEntity` + cele 5 rute existente) — tabela nouă e adăugare.
- Nu plasa apelul de audit înainte de confirmarea reușitei operațiunii reale — ordinea corectă e: schimbarea de business reușește → apoi audit, în aceeași tranzacție.
- Nu serializa entități întregi în `valori_vechi`/`valori_noi` — doar câmpurile din tabelul de mai sus.

## Criterii de acceptare

1. Toate cele 17 puncte de apel confirmate una câte una, cu status din Pas 0 (nu presupuse).
2. Migrarea V8 rulează curat, fără conflict cu alte scripturi din echipă.
3. `GET /api/admin/audit-log` funcțional, paginat, doar pentru rol `ADMIN`.
4. Pagina de admin afișează nume de utilizatori, nu ID-uri brute Keycloak.
5. Niciun apel de audit nu apare în afara tranzacției operațiunii pe care o descrie.
