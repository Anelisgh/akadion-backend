# Documentație: Reguli de Business (Core Business Rules)

> Actualizat 2026-08-13 după restructurarea package-by-feature (numele claselor de service rămân aceleași, doar pachetele s-au mutat — vezi `configurari.md` §1) și după consolidarea excepțiilor (vezi `exceptii.md`).

Acest document sumarizează regulile fundamentale de business aplicate în serviciile backend, acoperind gestiunea cursurilor, a săptămânilor, a documentelor, sistemul de aprobare utilizatori și integrarea cu sistemele externe (MinIO, Keycloak, RAG). Ele dictează comportamentul strict al aplicației.

---

## 1. Reguli Administrative & Fluxul de Aprobare Utilizatori
- **Înregistrare:** Utilizatorul se înregistrează direct în Keycloak. După validarea adresei de e-mail acolo, ajunge pe backend.
- **Creare Cont Local:** Dacă utilizatorul are un ID Keycloak neînregistrat încă în DB locală, se creează un rând în tabela `app_user` cu starea `INCOMPLET`.
- **Completarea Profilului:** 
  - Doar conturile `INCOMPLET` pot seta profilul (Nume, Prenume, Facultate și Rol). 
  - Trecerea din `INCOMPLET` duce la starea `PENDING`. Rolul este asignat acum (`STUDENT` sau `PROFESOR`), dar contul rămâne blocat până la intervenția adminului. NU se poate alege rol `ADMIN` de către un utilizator.
- **Aprobarea Admin:**
  - **Acceptare:** Trecere din `PENDING` în `ACTIV`. Contul devine funcțional pe deplin.
  - **Respingere:** Trecere din `PENDING` în `RESPINS`. Se incrementează un contor de respingeri. Utilizatorul nu e șters, el își mai poate accesa pagina `/complete-profile` de pe React ca să remiteze informațiile până va fi acceptat, teoretic (desi limitat momentan la INCOMPLET la nivel de serviciu).
- **Dezactivarea unui Cont Activ (Banare):** Setează `INACTIV` + blochează Keycloak. Declanșează dezactivarea cursurilor (pentru profesori) și înrolărilor (pentru studenți) deținute de acest utilizator.
- **Simetria Reactivării:** Când un admin deblochează un cont (trece înapoi la `ACTIV`), entitățile dependente (ex: cursurile) RĂMÂN INACTIVE (dezactivate). Profesorul trebuie să și le activeze manual de pe contul său dacă a fost debanat.

## 2. Reguli privind Cursurile (`CursService`)
- **Data de finalizare (`DATA_SFARSIT`):**
  - Este opțională doar dacă nu există `DATA_INCEPUT`.
  - Recalculare automată: Niciodată introdusă de profesor! Se calculează prin `DATA_INCEPUT + (număr săptămâni curente * 7 zile - 1 zi)`. Această funcție se declanșează oricând este adăugată, ștearsă o săptămână sau cursul e modificat.
- **Dezactivarea/Activarea unui Curs (Soft Delete):**
  - Când un curs e dezactivat de un profesor, **toate înrolările active ale studenților** la acel curs sunt de asemenea dezactivate (`activ=false`).
  - La reactivare (`activ=true`), doar cursul redevine public. Înrloările vechi rămân inactive, forțând studenții să refacă actul de înscriere conștient.

## 3. Reguli privind Săptămânile (`SaptamanaService`)
- **Auto-Numerotare:** Numărul unei săptămâni (`NR_SAPTAMANA`) este invariabil generat automat pe server. Frontend-ul nu are control asupra lui.
- **Ștergerea (Hard Delete Restrictiv):**
  - Este strict interzisă ștergerea oricărei săptămâni cu excepția **ultimei săptămâni** din cronologia cursului.
  - Ștergerea ultimei săptămâni provoacă o cascadă de distrugere masivă DB: 
    1. Șterge bifele de progres (`PARCURS`) pe acea săptămână de la toți studenții.
    2. Declanșează distrugerea documentelor asociate (atât MinIO, cât și DB, cât și rețeaua de embeddings din FastAPI/RAG).
    3. Șterge `SAPTAMANA`.
    4. Recalculează `DATA_SFARSIT` pentru curs.

## 4. Reguli privind Documentele (`DocumentService`)
- **Operațiunea de Upload (Extensii permise: `pdf`, `docx`, `pptx`, `zip`):**
  - Stochează fizic în MinIO blob-ul curent.
  - Salvează `DOCUMENT` cu `status_index = PRELUAT`.
  - Declanșează best-effort transfer către sistemul inteligent RAG prin apel POST.
  - Schimbă la rând statusul în `TRIMIS` (succes) sau `ERONAT` (dacă RAG-ul dă eroare).
- **Soft Delete Documente:** Spre deosebire de săptămâni, documentele pot fi "soft deleted" individual (`activ=false`). Rămân în MinIO arhivate. Embeddings-urile din RAG sunt însă șterse automat la soft delete, păstrând asistentul AI necontaminat cu materiale vechi/restrase.
- **Inlocuirea (Update):** Orice editare care conține un nou fișier face un overwrite: trimite din nou request la RAG spre a rescrie logica din vectori.

## 5. Reguli privind Studenții, Chatbot-ul Aky și Quiz-uri (`StudentCursService`, `StudentQuizService`, `StudentAkyService`, `ConversatieService`)

> `StudentCursService` a fost despărțit (2026-08-12/13) în trei: `StudentCursService` rămâne cu înscriere/progres/documente (și expune `determinaSaptamanaParcursaMax`/`verificaRateLimitAky`, folosite de celelalte două), `StudentQuizService` preia orchestrarea quiz-urilor, `StudentAkyService` preia chat-ul Aky în timp real + flashcards. Regulile de business de mai jos rămân valabile, doar clasa care le implementează s-a schimbat.
- **Logica de Înscriere:** Tabela `USER_CURS` are unique-constraint (`studentId, cursId`). Dacă un student revine la un curs abandonat, codul face un "upsert": activează înregistrarea veche punând `activ=true` pentru a păstra trasabilitatea istorică a progresului.
- **Bifarea Progresului:** Doar un student înscris `activ=true` pe acel curs are permisiunea de a bifa o săptămână ca finalizată (adăugare rând în `PARCURS`).
- **Limitarea AI (Aky & Quiz):** `RateLimiterService` (componentă generică, partajată) — rate-limiter in-memory cu fereastră glisantă (`ConcurrentHashMap<String, Deque<Instant>>`), cheie compusă pe caz de utilizare, nu doar pe utilizator: `"student-aky:" + studentId` pentru chat/quiz/flashcards student (comun, deci un student poate epuiza limita pe una din cele trei fără să afecteze celelalte două — le partajează), `"conversatie:" + userId` pentru chat-ul persistat (`ConversatieService`), separat. Limita este de maxim 10 cereri/minut per cheie.
- **Securitate & Bounding Context AI:** O întrebare sau cerere de quiz este permisă doar dacă utilizatorul este înrolat activ (sau este profesorul cursului). Generarea de quiz limitează automat documentele la săptămânile parcurse (`<= maxSaptamanaParcursa`).
- **Orchestrare Conversații Chat (3 Pași):**
  1. Salvarea întrebării utilizatorului (`@Transactional`) cu `are_raspuns = false`.
  2. Apel HTTP extern către RAG (Fără `@Transactional`) pentru a preveni blocarea pool-ului de conexiuni DB.
  3. Salvarea răspunsului ASISTENT-ului (`@Transactional`) și marcarea `are_raspuns = true`.
- **Mecanismul de Retry:** Dacă pasul 2 eșuează, mesajul rămâne cu `are_raspuns = false`. Utilizatorul poate reîncerca răspunsul direct pe mesajul existent fără re-crearea întrebării.


## 6. Actualizarea Profilului și a Email-ului (`UserProfileService`)
- **Tranzacție Distribuită pe Mail:** Deoarece aplicația deține o "dublă-sursă" de email (Keycloak vs DB), modificarea adresei de mail este periculoasă și tratată strict:
  1. Se verifică duplicatul în local.
  2. Modifică emailul în Keycloak.
  3. Modifică emailul în DB-ul local și dă commit la tranzacție.
  4. Dacă DB-ul local pică din motive de date corrupt/race, există block `catch` care apelează iar Keycloak pentru a reveni la e-mailul originar! E un compensator "Saga".
- **Reset Parola:** Spring nu preia efectiv niciodată parolele; declanșează direct prin API `UPDATE_PASSWORD` request un flux e-mail direct din Keycloak.
