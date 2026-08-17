# Flux 1: Autentificare, Înregistrare și Aprobare Cont

> Actorii: **Utilizator nou** (Student/Profesor candidat) + **Admin**.
> Referințe tehnice complete: `../auth_backend_keycloak.md`, `../api-controllers.md` §2 și §5, `../services.md` §1-2, `../README.md`.

---

## De ce arată așa fluxul (ideea de fond)

Aplicația **nu ține parole**. Toată identitatea (email + parolă, verificare email, resetare parolă) e delegată total către **Keycloak**. Backend-ul Spring Boot are însă propria bază de date locală (PostgreSQL) care e sursa de adevăr pentru **ce rol are utilizatorul** și **dacă contul lui e activ**. Motivul acestei separații: un admin trebuie să poată aproba/respinge/bloca un cont fără să atingă Keycloak (mai rapid, mai sigur, nu depinde de disponibilitatea Keycloak pentru operațiuni de zi cu zi), iar Keycloak rămâne responsabil doar de partea grea criptografic (parole, sesiuni, email-uri de verificare).

Asta produce nevoia unui **workflow de aprobare în 3 etape**: cont schelet → profil completat → aprobat de admin. Un cont din Keycloak nu e automat funcțional în Akadion.

---

## Pasul 1 — Înregistrare în Keycloak

**Ce se întâmplă:** Utilizatorul apasă "Înregistrare" pe frontend. E dus direct pe pagina de Sign Up a Keycloak (nu există formular de înregistrare în aplicația noastră). Introduce email + parolă acolo.

**Cum (tehnic):** Frontend-ul redirecționează la `/oauth2/authorization/keycloak-register`. `CustomAuthorizationRequestResolver` intercepteaza această rută specifică și adaugă programatic `prompt=create` la URL-ul generat spre Keycloak, ceea ce forțează Keycloak să deschidă direct tab-ul de înregistrare (nu login). Parola nu tranzitează niciodată prin backend-ul Akadion — totul se întâmplă direct în formularul Keycloak.

**De ce așa:** Dacă am fi construit propriul formular de înregistrare, am fi devenit responsabili de hashing parole, validare complexitate, verificare email etc. — exact ce Keycloak deja face corect. Separarea completă e o alegere de securitate, nu doar de comoditate.

---

## Pasul 2 — Prima autentificare → creare cont schelet

**Ce se întâmplă:** Keycloak validează credențialele și redirecționează utilizatorul înapoi către backend, apoi către frontend. Din perspectiva utilizatorului, nu vede nimic intermediar — ajunge direct pe pagina potrivită stării lui (ecran de completare profil, ecran de așteptare, sau aplicația propriu-zisă).

**Cum (tehnic):**
1. Keycloak trimite un callback OIDC către backend.
2. `CustomAuthenticationSuccessHandler` preia controlul. Caută în `app_user` un rând cu `id_keycloak` egal cu `sub`-ul (identificatorul unic) din tokenul OIDC.
3. **Dacă nu găsește nimic** (primul login al acestui utilizator): creează instant un rând "schelet" în `app_user` — doar `id_keycloak` și `mail` completate, `stareCont = INCOMPLET`, restul (`nume`, `prenume`, `facultate`, `rol`) rămân `NULL`.
4. **În ambele cazuri** (cont nou creat sau cont existent), backend-ul redirecționează HTTP 302 la un singur URL fix: `{frontend}/` — nu la o rută specifică stării.
5. Frontend-ul (deja are sesiunea/cookie-ul setat de backend) apelează `GET /api/auth/me`, citește `stareCont`, și un component de gardă (`RequireAuthenticatedState` din `App.jsx`, folosind maparea `routeByState`) redirecționează client-side la pagina corectă: `/complete-profile` (`INCOMPLET`), `/asteptare-aprobare` (`PENDING`), `/cerere-respinsa` (`RESPINS`), `/cont-dezactivat` (`INACTIV`), sau aplicația (`ACTIV`).

**De ce așa:** Crearea rândului se face în interiorul unei metode `@Transactional` dedicate (nu direct în filtrul de securitate, care nu are context tranzacțional), pentru că audit log-ul (vezi `../explicatie_audit_jurnal.md` §3) cere obligatoriu o tranzacție deschisă — o creare de cont în afara unei tranzacții n-ar putea fi jurnalizată corect.

**De ce backend-ul nu alege el pagina exactă:** Frontend-ul trebuie oricum să știe să facă această redirecționare pe baza `stareCont` de fiecare dată când userul navighează sau reîncarcă pagina (nu doar la login — de exemplu când un admin aprobă un cont în timp ce userul are tab-ul deschis). Dacă backend-ul ar mai calcula și el separat "pe ce rută trimit browserul", aceeași regulă `stareCont → pagină` ar exista dublat, în două limbaje, riscând să divergă dacă cineva redenumește o rută doar într-o parte. Redirecționarea unică la `/` elimină duplicarea: o singură sursă de adevăr pentru rutare (frontend), indiferent dacă declanșatorul e login-ul sau o schimbare de stare mid-sesiune.

---

## Pasul 3 — Completarea profilului

**Ce se întâmplă:** Utilizatorul e pe pagina `/complete-profile`. Introduce Nume, Prenume, Facultate, și alege rolul dorit — **Student** sau **Profesor**. Apasă trimite.

**Cum (tehnic):**
- Endpoint: `POST /api/auth/complete-profile`, body `CompleteProfileRequestDto (nume, prenume, facultate, rolDorit)`.
- `CompleteProfileService.completeaza(...)`: verifică strict că starea curentă e `INCOMPLET` (altfel `InvalidUserStateException`, 400). Verifică unicitatea email-ului (protecție împotriva unui alt `id_keycloak` care ar fi înregistrat deja același email — altfel `ForbiddenOperationException`/`EmailDuplicatException`). Salvează câmpurile, asignează rolul cerut, și trece `stareCont` direct în `PENDING`.
- Frontend redirecționează spre `/asteptare-aprobare`.

**De ce așa:** Rolul `ADMIN` **nu poate fi ales** din acest formular — e validat explicit (`rolDorit` acceptă doar `STUDENT`/`PROFESOR`). Un admin se creează manual, direct în baza de date (vezi `../README.md` — bootstrap admin), tocmai ca nimeni să nu poată deveni admin prin auto-servire.

---

## Pasul 4 — În așteptare, ce poate face contul înainte de aprobare

**Ce se întâmplă:** Cât timp contul nu e `ACTIV`, utilizatorul e blocat pe un ecran de așteptare — nu poate accesa nimic din aplicație.

**Cum (tehnic):** `StareContFilter` (un `OncePerRequestFilter`) verifică starea contului la **fiecare** cerere HTTP și aplică o whitelist strictă:

| Stare cont | Rute permise |
|---|---|
| autentificat, dar fără rând în `app_user` | 403 direct |
| `INCOMPLET` | `GET /api/auth/me`, `POST /api/auth/complete-profile`, `/logout` |
| `PENDING` | `GET /api/auth/me`, `/logout` |
| `RESPINS` | ca `INCOMPLET` (teoretic ar trebui să permită re-completare) |
| `INACTIV` | `GET /api/auth/me`, `/logout` |
| `ACTIV` | acces liber, filtrat mai departe de `@PreAuthorize` pe fiecare endpoint |

În plus, `CustomAuthoritiesMapper` suprascrie complet rolurile din tokenul Keycloak cu ce e în DB local — și dacă starea **nu** e `ACTIV`, returnează o listă goală de autorități, ceea ce face ca orice `@PreAuthorize("hasRole(...)")` să eșueze automat, indiferent de rol.

**⚠️ Inconsistență cunoscută (documentată, nu remediată):** `StareContFilter` permite unui utilizator `RESPINS` să apeleze din nou `POST /complete-profile` (ca și cum ar fi `INCOMPLET`), dar `CompleteProfileService` acceptă strict resubmisii doar din starea `INCOMPLET` — deci în practică orice re-submisie a unui cont respins eșuează cu `InvalidUserStateException`. Comportamentul de "re-aplicare după respingere" nu funcționează în implementarea curentă, deși filtrul sugerează că ar trebui. Vezi `../README.md` (secțiunea CAUTION).

---

## Pasul 5 — Decizia adminului

**Ce se întâmplă:** Adminul deschide dashboard-ul, vede lista cererilor `PENDING`, și decide: aprobă sau respinge.

**Cum (tehnic):**
- `GET /api/admin/users?stare=PENDING` → `AdminUserService.listaUtilizatori`, exclude automat conturile `ADMIN` din rezultat.
- **Aprobare:** `PATCH /api/admin/users/{id}/approve` → starea trece `PENDING → ACTIV`. Rolul rămâne cel ales la completarea profilului (nu se schimbă aici). Nimic nu se atinge în Keycloak — pur local.
- **Respingere:** `PATCH /api/admin/users/{id}/reject` → starea trece `PENDING → RESPINS`, contorul `nrRespingeri` se incrementează (vizibil în UI ca "a mai fost respins de N ori"). Contul nu se șterge nicăieri.
- Ambele operațiuni verifică prealabil starea (`InvalidUserStateException` dacă nu e `PENDING`) și că ținta nu e deja `ADMIN` (`ForbiddenOperationException`).

**De ce așa:** Aprobarea/respingerea sunt operațiuni pur locale, fără apel către Keycloak — motivul e disponibilitate și viteză (nu ai nevoie ca Keycloak să fie sus ca să aprobi 50 de cereri), și pentru că decizia de acces la platformă e o regulă de business a Akadion, nu ceva ce ține de identitate.

---

## Diagramă

```
[Utilizator]
   │ Sign Up (email+parolă, direct în Keycloak)
   ▼
[Keycloak] ── validează ──> callback OIDC
   ▼
[CustomAuthenticationSuccessHandler]
   │ id_keycloak nou? ──da──> creează app_user (stareCont=INCOMPLET)
   │                    nu──> (nimic de creat)
   ▼
redirect HTTP 302 la {frontend}/  (mereu același URL, indiferent de stare)
   ▼
[Frontend] GET /api/auth/me → citește stareCont → routeByState + RequireAuthenticatedState
   redirecționează client-side la pagina corectă (StareContFilter validează în paralel fiecare cerere API)

[/complete-profile] ── POST complete-profile ──> stareCont=PENDING (rol ales acum)
   ▼
[/asteptare-aprobare] ── blocat total în afară de GET /me, /logout

[Admin dashboard] ── GET /admin/users?stare=PENDING
   ├─ approve ──> stareCont=ACTIV  (acces complet, filtrat pe rol)
   └─ reject  ──> stareCont=RESPINS, nrRespingeri++ (blocat, re-submisie nefuncțională)
```

---

## Ce poate merge prost

| Situație | Ce vede utilizatorul | Cod |
|---|---|---|
| Completează profilul de două ori | `InvalidUserStateException` — starea nu mai e `INCOMPLET` a doua oară | 400 |
| Alege rol `ADMIN` în formular | Validare respinsă la nivel de DTO | 400 |
| Email deja folosit de alt cont Keycloak | `ForbiddenOperationException` / `EmailDuplicatException` | 403/409 |
| Cont `RESPINS` încearcă să re-aplice | `InvalidUserStateException` (inconsistență cunoscută, vezi Pas 4) | 400 |
| Utilizator nou, dar fără rând `app_user` (edge-case teoretic) | 403 direct din `StareContFilter` | 403 |
| Cont `PENDING`/`INACTIV` încearcă un endpoint protejat | `AccessDeniedException` (autorități goale din `CustomAuthoritiesMapper`) | 403 |
