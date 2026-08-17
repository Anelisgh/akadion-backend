# Flux 2: Administrare Utilizatori (Dezactivare/Reactivare) și Audit Log

> Actor principal: **Admin**.
> Referințe tehnice complete: `../services.md` §1 și §14, `../api-controllers.md` §1, `../business-rules.md` §1, `../explicatie_audit_jurnal.md`.

---

## Partea A — Dezactivare și Reactivare Cont

### De ce există acest flux (dincolo de aprobare/respingere)

Aprobare/respingere (flux 1) sunt tranziții unidirecționale la începutul vieții unui cont. Dar un cont **deja activ** poate avea nevoie să fie blocat mai târziu (comportament abuziv, cerere GDPR, cont părăsit) — și de aici apare o problemă reală: dacă blochezi un profesor, ce se întâmplă cu cele 5 cursuri și 200 de studenți înscriși la el? Dacă blochezi un student, ce se întâmplă cu progresul lui? Fluxul de dezactivare/reactivare există special pentru a răspunde la asta, cu **efecte în cascadă controlate**.

### Ce se întâmplă — Dezactivare

1. Adminul apasă "Dezactivează" pe un cont `ACTIV`.
2. `POST /api/admin/users/{id}/deactivate` → `AdminUserService.dezactiveazaUser`.
3. Local (într-o metodă `@Transactional`): starea trece `ACTIV → INACTIV`.
4. **Dacă utilizatorul e `PROFESOR`**: toate cursurile lui trec `activ=false` (cascadă prin `CursService`).
5. **Dacă utilizatorul e `STUDENT`**: toate înrolările lui (`UserCurs`) trec `activ=false`.
6. Abia **după** ce partea locală a reușit, se apelează `KeycloakAdminService` pentru a bloca efectiv contul în Keycloak (`enabled=false`), ca utilizatorul să nu se mai poată loga deloc.

### Ce se întâmplă — Reactivare

1. `POST /api/admin/users/{id}/activate` → starea trece `INACTIV → ACTIV`. Contul e reactivat și în Keycloak.
2. **Cursurile/înrolările RĂMÂN inactive.** Reactivarea NU le repune automat pe `activ=true`.

### De ce reactivarea nu e simetrică cu dezactivarea

Aceasta e o decizie de business explicită, nu un bug: dacă un profesor a fost blocat 3 luni, cursurile lui probabil au conținut învechit sau au nevoie de revizuire înainte să fie redeschise studenților. Simetria automată (reactivezi profesorul → toate cursurile redevin publice instant) ar fi riscantă. Așa că profesorul trebuie **conștient** să repornească fiecare curs pe care vrea să-l redeschidă. Studenții, similar, trebuie să se reînscrie explicit.

### De ce Keycloak se apelează *după* baza de date locală, nu înainte, și de ce nu e o tranzacție unică

- **Ordinea**: dacă apelul spre Keycloak eșuează (Keycloak jos, timeout), partea locală (DB) tot a reușit — utilizatorul e deja blocat din perspectiva aplicației (starea `INACTIV` îl scoate din `StareContFilter`), chiar dacă mai poate teoretic obține un token nou din Keycloak câteva minute până la retry. E o alegere de tip "fail-safe local, best-effort extern" — aceeași filozofie ca la RAG (vezi flux 4 și 6).
- **Nu e o tranzacție unică** pentru că Keycloak nu participă la tranzacțiile PostgreSQL — sunt două sisteme separate, deci operațiunea e "best-effort", nu ACID. Modificarea locală se face printr-o metodă `@Transactional` dedicată, apoi se apelează Keycloak în afara ei, exact pentru a nu ține o tranzacție DB deschisă în timp ce se așteaptă un răspuns HTTP extern.

### Reguli suplimentare (garduri de siguranță)

- Adminul curent **nu se poate dezactiva pe el însuși**.
- Conturile `ADMIN` nu pot fi gestionate din acest flux deloc (nici listate, nici dezactivate) — filtrate explicit peste tot în `AdminUserService`.
- `InvalidUserStateException` (400) dacă se încearcă dezactivare pe un cont care nu e `ACTIV`, sau activare pe unul care nu e `INACTIV`.

---

## Partea B — Dashboard Admin (context, nu doar acțiuni)

`GET /api/admin/stats` oferă panoul de sus al dashboard-ului: număr cursuri active/inactive, utilizatori activi/pending. `GET /api/admin/cursuri` (+ variantele `/{id}`, `/{id}/saptamani`, `/{id}/studenti`, `/{id}/profesor`) dau adminului o vedere **read-only** peste orice curs din platformă, indiferent de proprietar — util pentru moderare, fără a-i da drepturi de editare (creare/editare cursuri rămâne strict la profesor, vezi flux 4).

---

## Partea C — Audit Log

### De ce există

Orice acțiune administrativă sau sensibilă (aprobare, respingere, dezactivare, ștergeri) trebuie să poată răspunde ulterior la: **cine**, **când**, **ce s-a schimbat exact**. Fără asta, o dispută ("cine mi-a dezactivat cursul?") n-ar avea răspuns verificabil.

### Ce se întâmplă tehnic

- Orice serviciu care execută o acțiune sensibilă apelează `AuditLogService.inregistreaza(...)` la finalul operațiunii, salvând: tabelul afectat, ID-ul înregistrării, tipul operației (CREATE/UPDATE/DELETE), valorile vechi și noi (JSONB), și autorul.
- `AuditLogService` e marcat `@Transactional(propagation = Propagation.MANDATORY)` — **refuză să scrie dacă nu e apelat dintr-o tranzacție deja deschisă**. Asta previne loguri "orfane" (scrise, dar fără ca operațiunea de bază să fi reușit cu adevărat) — dacă tranzacția principală face rollback, logul de audit face rollback odată cu ea.
- **Autorul nu vine niciodată din ce trimite frontend-ul.** Se citește exclusiv din `SecurityContextHolder` (`auditorProvider.getCurrentAuditor()`), exact mecanismul folosit și de `AuditConfig` pentru `createdBy`/`updatedBy` (vezi `../configurari.md` §2.1). Dacă acțiunea vine dintr-un proces automat fără utilizator logat (ex: seeding la pornirea aplicației), autorul e `"system"`.

### De ce a fost proiectat exact așa (detalii complete: `../explicatie_audit_jurnal.md` §6)

- **Cascadele NU generează loguri individuale.** Dacă dezactivarea unui profesor dezactivează în cascadă 10 cursuri, se scrie **un singur** log ("Dezactivare Profesor"), nu 10. Codul de cascadă ocolește intenționat metodele publice auditate (ex: la ștergerea unei săptămâni se apelează direct `documentRepository.deleteAll()`, nu metoda de soft-delete individuală a fiecărui document) — principiul KISS: jurnalul rămâne citibil, nu inundat.
- **Upload-ul de documente se auditează pe baza succesului local, indiferent de răspunsul RAG.** Dacă fișierul a ajuns fizic în MinIO și în DB, operațiunea "a reușit" din perspectiva utilizatorului — chiar dacă RAG a răspuns cu eroare (`statusIndex=ERONAT`). Consecință directă a principiului "RAG e best-effort" (vezi flux 4/6).
- **Nu se folosește `@Data`/`toString()` automat pe entitatea `AuditLog`.** `valori_vechi`/`valori_noi` conțin frecvent date personale (email, nume) — un `println(auditLog)` accidental într-un log de debug ar fi scurs date GDPR direct în consolele serverului. Eliminarea `@Data` a fost o decizie explicită de securitate.
- **Index pe coloana `utilizator`** — fără el, filtrarea din UI-ul de admin pe un anumit utilizator ar deveni lent pe măsură ce jurnalul crește la mii de rânduri.

### Cum îl vede adminul

`GET /api/admin/audit-log?page=0&size=20` → `AuditLogService.getAuditLog(Pageable)` → `AdminController`, returnează un `Slice<AuditLogDto>` paginat.

---

## Diagramă

```
Admin apasă "Dezactivează" pe un PROFESOR activ
        │
        ▼
AdminUserService.dezactiveazaUser (local, @Transactional)
        │
        ├─> stareCont: ACTIV → INACTIV
        ├─> cascadă: toate cursurile profesorului → activ=false
        └─> AuditLogService.inregistreaza("USER_DEACTIVATED", ...)  [1 singur log]
        │
        ▼ (după commit local, best-effort)
KeycloakAdminService.dezactiveaza(idKeycloak)  ── dacă Keycloak e jos, nu se face rollback local
```

---

## Ce poate merge prost

| Situație | Ce se întâmplă | Cod |
|---|---|---|
| Dezactivare pe cont deja `INACTIV` | `InvalidUserStateException` | 400 |
| Admin încearcă să se dezactiveze pe sine | `ForbiddenOperationException` | 403 |
| Admin încearcă să dezactiveze alt `ADMIN` | Nu apare în listă / `ForbiddenOperationException` | 403 |
| Keycloak jos la momentul dezactivării | Local reușește, contul e blocat de `StareContFilter` oricum; Keycloak retry manual necesar | — |
| `AuditLogService` apelat fără tranzacție deschisă | Eroare la runtime (bug de programare, nu caz de business) — previne loguri orfane | 500 |
