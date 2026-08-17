# Fluxurile Aplicației Akadion — Index

> Acest folder explică aplicația **pe flux de business**, nu pe strat tehnic. Fiecare fișier povestește un lanț complet: cine declanșează acțiunea, ce se întâmplă pas cu pas, prin ce clase trece, și **de ce** a fost construit așa (regula de business din spate, edge-case-ul care a impus decizia).
>
> Pentru detalii exhaustive per-clasă (fiecare metodă, fiecare DTO, fiecare status HTTP), documentele tehnice din `../` (`services.md`, `api-controllers.md`, `README-ENTITATI.md`, `exceptii.md`, `business-rules.md`, `contract-rag.md`, `configurari.md`) rămân sursa de adevăr — fișierele de aici le leagă între ele și le dau sens narativ. Nu duplicăm acolo unde un link e suficient.
>
> Actualizat: 2026-08-17.

---

## Cum se citește un document de flux

Fiecare fișier din acest folder urmează aceeași structură:
1. **Declanșator** — ce apasă/trimite utilizatorul, din ce stare pleacă.
2. **Pașii (Ce → Cum)** — narațiune pas cu pas: ce se întâmplă vizibil, apoi mecanismul tehnic exact (endpoint, service, tabelă) care îl produce.
3. **De ce așa** — motivația de business/tehnică din spatele fiecărei decizii ne-evidente, inclusiv inconsistențele cunoscute și lăsate intenționat.
4. **Diagramă** — schema succintă a fluxului.
5. **Ce poate merge prost** — excepțiile posibile și ce vede utilizatorul.

---

## Harta fluxurilor

| # | Fișier | Ce acoperă | Cine e actorul principal |
|---|---|---|---|
| 1 | [01-autentificare-si-onboarding.md](01-autentificare-si-onboarding.md) | Login prin Keycloak, creare cont schelet, completare profil, aprobare/respingere admin, stările contului | Utilizator nou + Admin |
| 2 | [02-administrare-utilizatori-si-audit.md](02-administrare-utilizatori-si-audit.md) | Dezactivare/reactivare cont (cu efecte în cascadă), dashboard admin, jurnalul de audit | Admin |
| 3 | [03-profil-propriu.md](03-profil-propriu.md) | Editare profil propriu, schimbare email (saga distribuită), resetare parolă | Orice utilizator logat |
| 4 | [04-gestionare-curs-saptamani-documente.md](04-gestionare-curs-saptamani-documente.md) | Creare/editare/dezactivare curs, adăugare/ștergere săptămâni, upload/înlocuire/ștergere documente + pipeline RAG | Profesor |
| 5 | [05-student-inscriere-si-progres.md](05-student-inscriere-si-progres.md) | Descoperire cursuri, înscriere/retragere, bifare progres săptămânal, acces documente | Student |
| 6 | [06-chat-aky-si-conversatii.md](06-chat-aky-si-conversatii.md) | Chat AI persistat (istoric) și chat AI în timp real (student), retry, rate limiting | Student + Profesor |
| 7 | [07-quiz-si-flashcards.md](07-quiz-si-flashcards.md) | Generare quiz, finalizare + corectare, istoric, generare flashcards | Student |

---

## Arhitectura pe scurt (context necesar pentru toate fluxurile)

- **Monolit modular Spring Boot 3 / Java 21**, organizat *pe feature* (nu pe strat): `admin/`, `akychat/`, `auth/`, `curs/`, `quiz/`, plus `common/` (entități/repo-uri partajate), `config/` și `exception/` (cross-cutting). Vezi `../configurari.md`.
- **Model de securitate BFF (Backend-for-Frontend)**: nu există parole în DB-ul aplicației. Totul trece prin **Keycloak** (OAuth2/OIDC). Sesiunea e păstrată server-side (cookie `JSESSIONID` + `XSRF-TOKEN`), nu JWT în frontend. PostgreSQL local e sursa de adevăr pentru **rol** și **stare cont** — Keycloak e sursa de adevăr doar pentru identitate/parolă.
- **Dublă sursă de adevăr controlată**: email-ul există și în Keycloak, și în DB locală — orice modificare a lui e tratată ca tranzacție distribuită (saga) cu compensare, pentru că cele două sisteme nu pot fi actualizate atomic împreună (vezi flux 3).
- **Integrare RAG (Python/FastAPI + Gemini + Qdrant), best-effort**: indexarea documentelor și chat-ul AI sunt sisteme externe. Regula constantă în toată aplicația: **dacă RAG e jos, fluxul local (DB + MinIO) tot reușește** — RAG doar marchează o stare de eroare recuperabilă (`ERONAT`) sau lasă un mesaj fără răspuns (`areRaspuns=false`), niciodată nu blochează operațiunea locală. Vezi `../contract-rag.md`.
- **Soft-delete peste tot, hard-delete doar unde e explicit motivat**: cursuri, înrolări, documente, conversații se dezactivează (`activ=false`) — păstrează istoric/audit. Excepția: ștergerea unei săptămâni (doar ultima, vezi flux 4) face hard-delete în cascadă, pentru că o săptămână la mijlocul cronologiei ar sparge numerotarea.
- **Audit Log**: acțiunile administrative/sensibile sunt jurnalizate cu autor determinat **exclusiv din `SecurityContext`** (niciodată din ce trimite frontend-ul) — vezi flux 2.
- **Rate limiting in-memory**: `RateLimiterService` — fereastră glisantă, 10 cereri/minut, chei diferite per context (`"student-aky:" + studentId` comun pentru chat+quiz+flashcards student; `"conversatie:" + userId` pentru chat persistat). Vezi flux 6 și 7.
- **Excepții → JSON uniform**: orice eroare de business ajunge la frontend prin `GlobalExceptionHandler`, mapată la un cod HTTP semantic (400/403/404/409/413/429/502). Detalii complete: `../exceptii.md`.

---

## Entitățile centrale (referință rapidă)

```
User (app_user) ──rol──> Rol (ADMIN/PROFESOR/STUDENT)
     │──stareCont──> StareCont (INCOMPLET/PENDING/ACTIV/INACTIV/RESPINS)
     │
     ├──(profesor)──> Curs ──> Saptamana ──> Document
     │                  │
     └──(student)──> UserCurs (înrolare) ──> Parcurs (bifă săptămână)
     │
     ├──> Conversatie ──> MesajChat   (chat AI persistat)
     ├──> IncercareQuiz                (quiz-uri)
     └──> AuditLog (ca autor, via createdBy)
```

Detalii complete (câmpuri, constrângeri, indecși): `../README-ENTITATI.md`.
