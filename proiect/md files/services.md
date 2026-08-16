# Documentație: Servicii

> Actualizat 2026-08-13. Pachetul unic `com.example.akadion.service` **nu mai există** — restructurarea package-by-feature (2026-08-13) a mutat fiecare service în pachetul `service` al feature-ului lui: `admin/` (plat, fără subpachet `service`), `akychat/service/`, `auth/service/`, `curs/service/`, `quiz/service/`. Vezi `configurari.md` §1 pentru maparea completă pachet↔feature.

Acest document descrie exhaustiv toate serviciile backend-ului, incluzând responsabilitățile lor, algoritmii importanți, tranzacțiile și edge-cases-urile.

---

## 1. `AdminUserService`
- **Scop:** Gestionează fluxul de aprobare a conturilor (aprobare, respingere, dezactivare, reactivare) de către un administrator.
- **Tranzacții:**
  - Metodele de citire (`listaUtilizatori`) folosesc `@Transactional(readOnly = true)`.
  - Modificările de stare locală (`approveUser`, `rejectUser`) folosesc `@Transactional`.
  - Modificările complexe (`dezactiveazaUser`, `activeazaUser`) folosesc o abordare "best-effort" fără tranzacție globală pentru a preveni blocarea DB dacă Keycloak este jos. Ele execută local modificările (printr-o metodă `@Transactional`), apoi apelează Keycloak.
- **Business Rules & Workflow:**
  - **Approve:** Schimbă starea din `PENDING` în `ACTIV`.
  - **Reject:** Schimbă starea din `PENDING` în `RESPINS` și incrementează contorul `nrRespingeri`.
  - **Dezactivează (Cascadă):** Trecerea în `INACTIV` dezactivează și cursurile profesorului (`activ = false`) și înrolările lui ca student. Administratorul curent (cel ce inițiază acțiunea) nu se poate dezactiva pe sine, iar alte conturi `ADMIN` nu pot fi modificate de aici.
- **Interacțiuni:** `UserRepository`, `StareContRepository`, `KeycloakAdminService`, `CursService`.

## 2. `CompleteProfileService`
- **Scop:** Salvează datele profilului pentru utilizatorii nou logați din Keycloak care se află în starea `INCOMPLET`.
- **Tranzacții:** `@Transactional` pe metoda principală `completeaza`.
- **Business Rules & Workflow:**
  - Doar conturile cu starea `INCOMPLET` au voie.
  - Asigură unicitatea adresei de email (previne cazurile în care alt ID Keycloak a înregistrat același email, aruncând `ForbiddenOperationException`).
  - Rolul dorit (`PROFESOR` sau `STUDENT`) devine rolul asignat, iar contul trece direct în `PENDING`.
  - Rolul de `ADMIN` nu poate fi solicitat din formular.
- **Edge cases:** Formulare re-trimise. Protejat de verificarea stării.

## 3. `CursService`
- **Scop:** Gestiunea cursurilor la nivel de bază de date, incluzând adăugare, listare, modificare și soft-delete.
- **Tranzacții:** `@Transactional` pe operațiunile de scriere, read-only pe citire.
- **Algoritmi & Business Rules:**
  - `recalculeazaDataSfarsit`: Dacă există o dată de început, se calculează adăugând (`nr_saptamani * 7 - 1`) zile. Se execută la modificarea cursului.
  - `dezactiveazaCurs`: Setează `activ = false` atât pe curs, cât și pe toate `UserCurs`-urile asociate (înrolările).
  - `activeazaCurs`: Setează doar cursul ca activ. Înrolările rămân inactive!
- **Securitate internă:** Majoritatea metodelor verifică ownership-ul prin `CursOwnershipValidator.verificaProprietar`/`verificaProprietarSauAdmin` (extras din `CursService`/`DocumentService`/`SaptamanaService`, unde era duplicat): validează că `profesorId` primit coincide cu `curs.getProfesor().getId()`, altfel aruncă `ForbiddenOperationException` (fostă `AccesInterzisException`, consolidată). Administratorul este by-passat pe read-only.

## 4. `DocumentService`
- **Scop:** Upload-ul materialelor și legătura cu modulul RAG și MinIO.
- **Workflow Upload:**
  1. Validare extensie (`pdf`, `docx`, `pptx`, `zip`).
  2. Apel `MinioStorageService.uploadFile`.
  3. Salvare `Document` în baza de date cu status `PRELUAT`.
  4. Apel sincron `ragIngestService.trimiteLaIngest`.
  5. Salvare finală `Document` cu status `TRIMIS` (succes) sau `ERONAT` (eșec RAG).
- **Workflow Soft-Delete:** Documentul se trece în `activ=false` în BD. Apelează `stergeDinIngest` spre RAG, pentru ca vectorii să fie șterși și ei. Nu se șterge fizic din MinIO, decât atunci când săptămâna întreagă este ștearsă.
- **Workflow Modificare (Înlocuire fișier):** Upload noul fișier â†’ Sterge vechiul din MinIO â†’ Trimite către RAG să re-vectorizeze â†’ Salvează. 

## 5. `KeycloakAdminService`
- **Scop:** Acționează ca un client HTTP care interacționează cu Admin REST API-ul de la Keycloak.
- **Responsabilități:** 
  - **Dezactivare / Reactivare:** Activează sau dezactivează conturi utilizând ID-ul de Keycloak (`sub`), prin modificarea parametrului `enabled`.
  - **Actualizare Email:** Schimbă adresa de e-mail în Keycloak folosind endpoint-ul de users (pentru consistența dublei surse de adevăr).
  - **Acțiuni Email:** Apelează `execute-actions-email` pentru a declanșa trimiterea e-mailurilor native din Keycloak pentru verificarea adresei de mail (`VERIFY_EMAIL`) sau resetarea parolei (`UPDATE_PASSWORD`). 
- **Tranzacții:** Utilizează un flux OAuth2 Client Credentials configurat global pentru obținerea automată a token-ului de admin `service-account`.

## 6. `MinioStorageService`
- **Scop:** Conexiunea raw cu serverul de stocare S3 (MinIO).
- **Algoritmi:** Generează o cale fixă `curs-{cursId}/saptamana-{saptId}/{uuid}-{fileName}`.
- **Responsabilități:** Upload, delete și obținerea de URL-uri pre-semnate (`PresignedUrl`) atât pentru vizualizare în browser (inline, ex: PDF-uri), cât și pentru descărcare.

## 7. `RagChatService` și `RagIngestService`
- **Scop:** Intermediază discuțiile cu API-ul extern de inteligență artificială FastAPI.
- **`RagIngestService`:** Folosește un RestClient pentru a face POST `/ingest` și DELETE `/ingest/{id}`. Prinde excepțiile și întoarce `false` la eșec (RAG unavailable nu strică fluxul bazei de date!).
- **`RagChatService`:** Formatează payload-ul pentru endpoint-ul `/chat`.

## 8. `SaptamanaService`
- **Scop:** Gestiunea cronologică a săptămânilor unui curs.
- **Business Rules:**
  - `nrSaptamana` este auto-generat la adăugare: caută maximul curent pe acel curs și adună 1.
  - Adăugarea declanșează automat `cursService.recalculeazaDataSfarsit`.
  - **Ștergerea** este extrem de strictă: este permisă **doar pe ultima săptămână**.
  - Ștergerea declanșează `hard delete` complet în cascadă (inclusiv din RAG, MinIO și `PARCURS`).

## 9. `StudentCursService` — despărțit în 3 (2026-08-12/13)

> `StudentCursService` (785 linii) amesteca înscriere/progres/documente + orchestrare quiz + chat Aky/flashcards. A fost despărțit pe responsabilitate. Toate trei sunt în pachetul `curs`/`quiz`/`akychat` (nu mai există un singur `StudentCursService` monolit).

### 9.1. `StudentCursService` (nucleu — rămas în `curs/service/`)
- **Scop:** Înrolarea studenților, tracking-ul bifărilor per săptămână, acces documente. Expune și `determinaSaptamanaParcursaMax`/`verificaRateLimitAky`, injectate de `StudentQuizService` și `StudentAkyService` (evită duplicarea calculului de progres/rate-limit în toate trei).
- **Algoritmi importanți:**
  - `inscriereCurs`: Caută dacă există istoric `UserCurs`. Dacă da, face update `activ=true`. Dacă nu, inserție.
  - `verificaRateLimitAky`: deleagă la `RateLimiterService.verificaLimita("student-aky:" + studentId, 10, 1 min)` — cheie comună pentru chat+quiz+flashcards student (o singură limită partajată între toate trei).
  - `Calcul progres`: `countCompletedSaptamani / totalSaptamani * 100`.
  - `determinaSaptamanaParcursaMax`: cea mai mare săptămână bifată + 1 (min 1), plafonat la total săptămâni curs — folosit ca `maxSaptamanaParcursa` trimis către RAG (chat, quiz, flashcards).

### 9.2. `StudentQuizService` (`quiz/service/`)
- **Scop:** Orchestrarea ciclului de viață al unei încercări de quiz (fetch progres, apel RAG, persistare) — deleagă interpretarea/gradingul datelor brute la `QuizGradingService`.
- **Algoritmi importanți:**
  - `genereazaQuiz`: verifică rate limit comun, calculează `maxSaptamana`, apelează RAG (`ragChatService.genereazaQuiz`, implicit `nrIntrebari=5`, `dificultate="MEDIU"`), salvează `IncercareQuiz` cu `status=GENERATA`.
  - `finalizeazaQuiz`: `findByIdForUpdate` (lock pesimist — previne finalizare concurentă dublă), respinge dacă deja `FINALIZATA` (`IncercareQuizFinalizataException`), cere exact `nrIntrebari` răspunsuri (index unic, în interval), deleagă corectitudinea per întrebare la `QuizGradingService.isQuizAnswerCorrect`, actualizează `IncercareQuiz` la `status=FINALIZATA` — **detaliile JSON sunt suprascrise cu feedback, istoricul de generare originar nu mai e recuperabil**.

### 9.3. `StudentAkyService` (`akychat/service/`)
- **Scop:** Chat Aky în timp real (nepersistat, spre deosebire de `ConversatieService`) + generare flashcards.
- **Algoritmi importanți:**
  - `intreabaAky`: verifică rate limit comun, calculează `maxSaptamana` real din progresul studentului, apelează `RagChatService.intreabaAky(studentId, cursId, request, maxSaptamana)` — deci chat-ul studentului e limitat efectiv la documentele parcurse (spre deosebire de chat-ul profesorului/persistat, care trimite implicit `maxSaptamanaParcursa=100`, vezi `contract-rag.md`).
  - `genereazaFlashcards`: similar cu generarea quiz-ului — dacă e specificat un `documentId`, validează că documentul e activ, aparține cursului și e în limita de progres, apoi apelează RAG și returnează lista brută (`List<Map<String, Object>>`, serializată direct ca array JSON de Spring).

### 9.4. `QuizGradingService` (`quiz/service/`, fără dependențe)
- **Scop:** Normalizarea datelor de quiz venite din RAG (structură nesigură `Map<String,Object>`) și verificarea corectitudinii unui răspuns — extrasă din `StudentQuizService` pentru testabilitate izolată (nu are dependențe Spring injectate).
- **`isQuizAnswerCorrect`** (3 pași, tolerant la formatul RAG): (1) literă student == literă corectă; (2) valoare aleasă == valoare corectă; (3) fallback — caută litera care corespunde valorii corecte, compară cu litera aleasă. Normalizare case/trim la fiecare pas.

### 9.5. Alte componente partajate extrase în aceeași rundă
- **`CursOwnershipValidator`** (`curs/service/`) — vezi §3 mai sus.
- **`DocumentUrlBuilder`** (`curs/service/`) — construiește URL-urile de preview/download document (`/api/documente/{id}/preview|download/{filename}`), extras din `DocumentService` + fostul `StudentCursService` unde era duplicat.
- **`RateLimiterService`** (`auth/service/`) — vezi `business-rules.md` §5 pentru chei.

## 10. `UserProfileService`
- **Scop:** Gestionarea profilului utilizatorului logat și actualizărilor acestuia (nume, email, reset parolă).
- **Workflow Actualizare Email:** Este un proces de tip "Distributed Saga". Schimbă emailul în Keycloak -> Schimbă emailul în DB locală cu `saveAndFlush`. Dacă apare eroare (ex: violare unique constraint în DB), compensează eroarea punând vechiul email înapoi în Keycloak.
- **Workflow Reset Parolă:** Apelează `KeycloakAdminService` pentru a executa o acțiune require de `UPDATE_PASSWORD`, trimițând utilizatorului un e-mail direct din Keycloak.

## 11. `ConversatieService`
- **Scop:** Gestionarea sesiunilor de chat persistent, a istoricului de mesaje și a logicii de retry pentru RAG.
- **Workflow & Tranzacționare în 3 Pași:**
  - **Pas 1 (`@Transactional`):** Salvează întrebarea utilizatorului în DB cu `rol = UTILIZATOR` și `areRaspuns = false`. Dacă este prima întrebare din conversație, creează o entitate `Conversatie` nouă cu titlul extras din primele max 40 de caractere.
  - **Pas 2 (Fără Tranzacție):** Apelează `RagChatService.intreabaAky` pentru a obține răspunsul de la modulul extern RAG FastAPI. Nu ține deschisă o tranzacție DB pe durata apelului HTTP extern.
  - **Pas 3 (`@Transactional`):** Salvează răspunsul primit de la RAG. Dacă primește succes, salvează `MesajChat` cu rol asistent. Dacă dă fail sau timeout (aici sau în timpul stream-ului), lasă `are_raspuns = false`, oferind mecanism de `retry` din frontend.
- **Retry Mechanism (`retryMesaj`):** În cazul în care RAG a dat eroare la pasul 2, mesajul utilizatorului rămâne cu `areRaspuns = false`. Utilizatorul poate apela `retryMesaj` pentru a relua pasul 2 și pasul 3 pentru un mesaj specific, fără a duplica mesajul de întrebare în istoric.
- **Stergere Soft:** Setează `activ = false` pe conversație (`stergeConversatie`), păstrând istoricul în DB pentru audit.

## 14. `AuditLogService`
- **Scop:** Interogarea și managementul înregistrărilor de log (Audit).
- **Rol de Securitate:** Funcțiile sunt destinate accesului cu rol de `ADMIN`.
- **Workflow:** Exposează metode de căutare / paginare (ex: `getAuditLog(Pageable)`) care sunt direct mapate către `AdminController` pentru a expune fluxul complet către interfața de frontend (Secțiunea Audit Log).
