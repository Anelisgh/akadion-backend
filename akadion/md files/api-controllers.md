# Documentație: API Controllers

> Actualizat 2026-08-13. Pachetul unic `com.example.akadion.controller` **nu mai există** — restructurarea package-by-feature a mutat fiecare controller în pachetul `controller` al feature-ului lui: `admin/` (plat), `akychat/controller/`, `auth/controller/`, `curs/controller/`, `quiz/controller/`. Rutele HTTP (`@RequestMapping`/`@GetMapping`/etc.) **nu s-au schimbat** — doar locația fișierelor și, pentru fostul `StudentController`, împărțirea pe 3 clase (vezi §7).

Acest document descrie exhaustiv structura, endpoint-urile și responsabilitățile controllerelor REST (API) ale aplicației. Acestea reprezintă punctul de intrare (entrypoint) pentru interfața frontend (React).

---

## 1. `AdminController`
- **Path de bază:** `/api/admin`
- **Securitate:** Toate endpoint-urile necesită rolul `ADMIN` (`@PreAuthorize("hasRole('ADMIN')")`).

### Endpoints:
1. `GET /users`
   - **Scop:** Listează utilizatorii în funcție de starea lor.
   - **Query Param:** `stare` (ex: `PENDING`, `ACTIV`, `ALL`). Implicit este `PENDING`.
   - **Request DTO:** N/A.
   - **Response DTO:** `List<UserPendingDto (id, nume, prenume, mail, facultate, rolDorit, nrRespingeriAnterioare, stare, createdAt)>`.
   - **Business Rules:** Caută după starea aleasă în DB. Filtrează rezultatele eliminând conturile de `ADMIN` (acestea nu sunt afișate/gestionate aici).
   - **Interacțiuni:** Apelează `AdminUserService.listaUtilizatori(stare)`.
   - **Excepții:** N/A.

2. `PATCH /users/{id}/approve`
   - **Scop:** Aprobă un utilizator aflat în așteptare (`PENDING`).
   - **Request DTO:** N/A.
   - **Response DTO:** `ActionResponseDto (message)`.
   - **Business Rules:** Schimbă starea utilizatorului din `PENDING` în `ACTIV` local.
   - **Side effects:** Modificări în tabela `app_user` (stare cont).
   - **Excepții:** `InvalidUserStateException` dacă starea utilizatorului nu e `PENDING`. `ResursaNegasitaException` dacă id-ul nu există (fostă `UserNotFoundException`, consolidată). `ForbiddenOperationException` dacă ținta este ADMIN.

3. `PATCH /users/{id}/reject`
   - **Scop:** Respinge cererea unui utilizator `PENDING`.
   - **Request DTO:** N/A.
   - **Response DTO:** `ActionResponseDto (message)`.
   - **Business Rules:** Schimbă starea din `PENDING` în `RESPINS` și incrementează `nr_respingeri`. Nu șterge din Keycloak (va fi redirecționat către completare profil la login).
   - **Excepții:** `InvalidUserStateException` dacă starea utilizatorului nu e `PENDING`.

4. `POST /users/{id}/deactivate`
   - **Scop:** Dezactivează un utilizator `ACTIV`.
   - **Response:** 200 OK.
   - **Business Rules:** Schimbă starea în `INACTIV`. Apelează `KeycloakAdminService` pentru a bloca contul în Keycloak (best-effort). Dacă utilizatorul are rol `PROFESOR`, dezactivează automat în cascadă toate cursurile lui (`ACTIV=false`). Dacă are `STUDENT`, dezactivează (logic) toate înrolările lui.
   - **Excepții:** `InvalidUserStateException` dacă starea nu e `ACTIV`.

5. `POST /users/{id}/activate`
   - **Scop:** Reactivează un utilizator `INACTIV`.
   - **Response:** 200 OK.
   - **Business Rules:** Schimbă starea în `ACTIV`. Deblochează în Keycloak. Cursurile (pentru profesori) rămân inactive; reactivarea lor este la latitudinea profesorului.
   - **Excepții:** `InvalidUserStateException` dacă nu e `INACTIV`.

6. `GET /cursuri`
   - **Scop:** Listează toate cursurile din sistem.
   - **Response:** `List<CursResponseDto (id, denumire, descriere, dataInceput, dataSfarsit, activ, nrSaptamaniCurente, profesorNume, profesorPrenume, nrStudentiInscrisi)>`.

7. `GET /audit-log`
   - **Scop:** Listează jurnalul de evenimente (Audit Log) pentru acțiunile de nivel administrator și modificările importante.
   - **Query Params:** `page` (default 0), `size` (default 20).
   - **Response:** `Slice<AuditLogDto>`.
   - **Interacțiuni:** Apelează `AuditLogService.getAuditLog`.

8. `GET /stats`
   - **Scop:** Returnează statistici pentru dashboard.
   - **Response:** `DashboardStatsDto (cursuriActive, cursuriInactive, utilizatoriActivi, utilizatoriPending)` (cursuriActive, cursuriInactive, utilizatoriActivi, utilizatoriPending).

8. `GET /cursuri/{id}`
   - **Scop:** Returnează detaliile unui curs (read-only pentru admin).
   - **Response:** `CursResponseDto (id, denumire, descriere, dataInceput, dataSfarsit, activ, nrSaptamaniCurente, profesorNume, profesorPrenume, nrStudentiInscrisi)`.

9. `GET /cursuri/{id}/saptamani`
   - **Scop:** Listează săptămânile cursului.
   - **Response:** `List<SaptamanaResponseDto (id, nrSaptamana, descriere)>`.

10. `GET /saptamani/{saptamanaId}/documente`
    - **Scop:** Listează documentele unei săptămâni.
    - **Response:** `List<DocumentResponseDto (id, titlu, statusIndex, activ, urlVizualizare, urlDescarcare)>`.

11. `GET /cursuri/{id}/studenti`
    - **Scop:** Listează studenții înscriși și activi din curs.
    - **Response:** `List<StudentCursDto (id, nume, prenume, facultate, mail)>`.

12. `GET /cursuri/{cursId}/profesor`
    - **Scop:** Returnează detaliile profesorului unui curs.
    - **Response:** `ProfesorDetaliiResponseDto (id, nume, prenume, mail, facultate)`.

---

## 2. `AuthController`
- **Path de bază:** `/api/auth`
- **Securitate:** N/A direct la nivel de clasă, dar filtrat la nivel de security config (permis stării `INCOMPLET` și `RESPINS`).

### Endpoints:
1. `POST /complete-profile`
   - **Scop:** Completează datele profilului după înregistrarea Keycloak.
   - **Request DTO:** `CompleteProfileRequestDto (nume, prenume, facultate, rolDorit)` (nume, prenume, facultate, rolDorit). Validare: rolDorit să fie `PROFESOR` sau `STUDENT`.
   - **Response DTO:** `CompleteProfileResponseDto (id, nume, prenume, mail, facultate, rolDorit, stare, createdAt, message)`.
   - **Business Rules:** Utilizatorul trebuie să aibă starea locală `INCOMPLET`. După completare, starea trece în `PENDING`. Verifică să nu existe conturi duplicat pe același email.
   - **Excepții:** `InvalidUserStateException` dacă starea nu e `INCOMPLET`. `ForbiddenOperationException` la email duplicat.

---

## 3. `CursProfesorController`
- **Path de bază:** `/api/profesor/cursuri`
- **Securitate:** Necesită rolul `PROFESOR` sau `ADMIN`. Metoda ce implică modificare este doar pentru `PROFESOR`.

### Endpoints:
1. `GET /`
   - **Scop:** Listează cursurile proprii (pentru profesor) sau toate (pentru admin).
   - **Response:** `List<CursResponseDto (id, denumire, descriere, dataInceput, dataSfarsit, activ, nrSaptamaniCurente, profesorNume, profesorPrenume, nrStudentiInscrisi)>`.

2. `GET /{id}`
   - **Scop:** Detalii curs. Pentru profesor, validează că este owner-ul cursului.

3. `GET /{id}/studenti`
   - **Scop:** Studenții activi. Validează ownership.

4. `POST /`
   - **Securitate:** Doar `PROFESOR`.
   - **Request DTO:** `CursRequestDto (denumire, descriere, dataInceput)`. Validare: denumire max 150 chars, descriere max 1000 chars.
   - **Response:** `CursResponseDto (id, denumire, descriere, dataInceput, dataSfarsit, activ, nrSaptamaniCurente, profesorNume, profesorPrenume, nrStudentiInscrisi)` (201 Created).
   - **Business Rules:** Cursul creat are `activ=true` implicit și nu are `dataSfarsit`.

5. `PUT /{id}`
   - **Securitate:** Doar `PROFESOR`.
   - **Request:** `CursRequestDto (denumire, descriere, dataInceput)`.
   - **Response:** `CursResponseDto (id, denumire, descriere, dataInceput, dataSfarsit, activ, nrSaptamaniCurente, profesorNume, profesorPrenume, nrStudentiInscrisi)`.
   - **Business Rules:** Recalculează `dataSfarsit` doar dacă `dataInceput` s-a modificat (pe baza numărului curent de săptămâni: `dataInceput + 7*saptamani - 1`).
   - **Excepții:** `ForbiddenOperationException` (dacă profesorul logat nu este owner; fostă `AccesInterzisException`, consolidată).

6. `PATCH /{id}/dezactiveaza`
   - **Securitate:** Doar `PROFESOR`.
   - **Response:** 200 OK.
   - **Business Rules:** Dezactivează (`activ=false`) cursul și toate înrolările studenților aferente lui.

7. `PATCH /{id}/activeaza`
   - **Securitate:** Doar `PROFESOR`.
   - **Response:** 200 OK.
   - **Business Rules:** Activează doar cursul (`activ=true`). Înrolările rămân inactive; studenții trebuie să se reînscrie.

8. `POST /{cursId}/chat`
   - **Securitate:** Doar `PROFESOR`.
   - **Request:** `AkyChatRequestDto (intrebare, istoricConversatie)`. Validare: întrebare non-blank, max 1000 caractere.
   - **Response:** `AkyChatResponseDto (raspuns, surseFolosite)`.
   - **Business Rules:** Verifică dacă profesorul este owner și transmite mesajul spre `RagChatService`. Interogare LLM backend intern.

---

## 4. `DocumentProfesorController`
- **Path de bază:** `/api/profesor`
- **Securitate:** Necesită rol `PROFESOR` sau `ADMIN` (citire), respectiv strict `PROFESOR` (scriere). Toate endpoint-urile validează ownership-ul profesorului.

### Endpoints:
1. `GET /saptamani/{saptamanaId}/documente`
   - **Response:** `List<DocumentResponseDto (id, titlu, statusIndex, activ, urlVizualizare, urlDescarcare)>`. Filtrează doar documentele `activ=true`.

2. `POST /saptamani/{saptamanaId}/documente`
   - **Consumes:** `multipart/form-data`.
   - **Parametri:** `file` (MultipartFile), `titlu` (String).
   - **Response:** `DocumentResponseDto (id, titlu, statusIndex, activ, urlVizualizare, urlDescarcare)` (201 Created).
   - **Business Rules:** Validare extensii (`pdf`, `docx`, `pptx`, `zip`). Upload pe server `MinIO`. Generare UUID. Creare în DB (Status `PRELUAT`). Declanșează best-effort către RAG (prin HTTP spre instanța FastAPI Python) și dacă RAG acceptă, setează status `TRIMIS`, altfel `ERONAT`. Rollback fizic din MinIO dacă eșuează DB.

3. `PUT /documente/{id}`
   - **Consumes:** `multipart/form-data`.
   - **Parametri:** `file` (opțional), `titlu` (opțional).
   - **Response:** `DocumentResponseDto (id, titlu, statusIndex, activ, urlVizualizare, urlDescarcare)`.
   - **Business Rules:** Dacă se dă un `file` nou, urcă noul fișier în MinIO. Șterge fișierul vechi din MinIO. Actualizează vectorii din RAG chemând serviciul de ingest din nou (suprascriere). Dacă `titlu` e diferit, actualizează în DB.

4. `DELETE /documente/{id}`
   - **Response:** 200 OK.
   - **Business Rules:** Soft-delete (setare `activ=false` în BD). Sterge vectorii rămași din RAG (best-effort) chemând RAG `/ingest/{id}` cu metoda DELETE.

5. `POST /documente/{id}/retry-ingest`
   - **Response:** `DocumentResponseDto (id, titlu, statusIndex, activ, urlVizualizare, urlDescarcare)`.
   - **Business Rules:** Permite profesorului să retrimita cererea de vectorizare / indexare la RAG dacă status-ul a rămas setat pe `ERONAT`. Excepție aruncată dacă e deja `TRIMIS`.

---

## 5. `MeController`
- **Path de bază:** `/api/auth/me`
- **Securitate:** Orice utilizator valid OIDC din backend are acces (filtrele de securitate permit rutărilor cu `/api/auth/me`).

### Endpoints:
1. `GET /`
   - **Scop:** Read date profil și rol curent.
   - **Response:** `UserMeDto (id, nume, prenume, mail, rol, facultate, stareCont)`. Orice stare a contului primește acces.

2. `PUT /`
   - **Request:** `UpdateProfileRequestDto (nume, prenume, facultate)` (nume, prenume, facultate).
   - **Response:** `UserMeDto (id, nume, prenume, mail, rol, facultate, stareCont)` cu valorile noi modificate.
   - **Business Rules:** Modifică câmpurile din tabelul `app_user`.

3. `PUT /email`
   - **Request:** `UpdateEmailRequestDto (newEmail)`.
   - **Response:** `UserMeDto (id, nume, prenume, mail, rol, facultate, stareCont)`.
   - **Business Rules:** Realizează update atât în DB locală cât și în Keycloak (ca o tranzacție distribuită best-effort). Setează `emailVerified = false` în Keycloak.

4. `POST /request-password-reset`
   - **Response:** 202 Accepted.
   - **Business Rules:** Trimite acțiune UPDATE_PASSWORD către Keycloak pentru sub-ul curent, declanșând e-mail-ul din Keycloak direct către utilizator. Nicio informație de parolă nu ajunge la nivelul backend-ului din Spring Boot.

---

## 6. `SaptamanaProfesorController`
- **Path de bază:** `/api/profesor`
- **Securitate:** Necesită rol `PROFESOR` sau `ADMIN` (doar citire). Permisiunile de owner sunt re-validate în serviciu.

### Endpoints:
1. `GET /cursuri/{cursId}/saptamani`
   - **Response:** `List<SaptamanaResponseDto (id, nrSaptamana, descriere)>`. Ordonat crescător după `nr_saptamana`.

2. `POST /cursuri/{cursId}/saptamani`
   - **Request:** `SaptamanaRequestDto (descriere)` (descriere).
   - **Response:** `SaptamanaResponseDto (id, nrSaptamana, descriere)` (201 Created).
   - **Business Rules:** `nr_saptamana` este calculat automat = ultimul număr max + 1. Recalculează automat `data_sfarsit` a cursului, dacă cursul are `data_inceput`.
   - **Excepții:** `SaptamanaConcurentaException` (dacă a apărut un conflict de unicitate pe baza de date).

3. `PUT /saptamani/{id}`
   - **Request:** `SaptamanaRequestDto (descriere)`.
   - **Response:** `SaptamanaResponseDto (id, nrSaptamana, descriere)`. Modifică strict descrierea. Nu se modifică numărul săptămânii.

4. `DELETE /saptamani/{id}`
   - **Response:** 200 OK.
   - **Business Rules:** Regula dură de business obligă ștergerea **doar a ultimei săptămâni**! Face cascada cu hard delete (curăță înregistrările de finalizare student `PARCURS`, documentele cu hard-delete pe RAG + MinIO).
   - **Excepții:** `IllegalArgumentException` dacă săptămâna cerută nu e ultima.

---

## 7. Fostul `StudentController` — despărțit în 3 controllere (2026-08-12/13)
- **Path de bază (toate trei):** `/api/student`
- **Securitate (toate trei):** Necesită rol `STUDENT`.
- Ruta rămâne `/api/student/...` identică pentru toate endpoint-urile de mai jos — doar clasa Java care le expune s-a schimbat, în oglindă cu split-ul serviciilor (`services.md` §9).

### 7.1. `StudentCursController` (`curs/controller/`) — înscriere, progres, documente
1. `POST /cursuri/{cursId}/inscriere`
   - **Response:** 200 OK.
   - **Business Rules:** Dacă există deja un istoric inactiv de `user_curs` pentru perechea `(studentId, cursId)`, pur și simplu updatează `activ=true`. Altfel, inserează intrare nouă. Validare dacă cursul există și este `activ`.
   - **Excepții:** `IllegalArgumentException` dacă studentul e deja înscris activ sau curs inactiv.

2. `POST /cursuri/{cursId}/retragere`
   - **Response:** 200 OK.
   - **Business Rules:** Setează înrolarea aferentă la `activ=false` din backend.

3. `GET /cursuri/disponibile`
   - **Response:** `List<CursDisponibilResponseDto (id, denumire, descriere, profesorNume, profesorPrenume, dataInceput, dataSfarsit, nrSaptamani)>`.
   - **Business Rules:** Listează exclusiv cursurile la care studentul nu este actualmente înrolat activ (`activ=true` pentru curs, dar niciun match valid `user_curs` pe `studentId`).

4. `GET /cursuri/mele`
   - **Response:** `List<CursInrolatResponseDto (id, denumire, descriere, dataInceput, dataSfarsit, profesorNume, profesorPrenume, procentajProgres, nrSaptamani)>`.
   - **Business Rules:** Listează cursurile unde există un rând `user_curs` cu `activ=true`. Calculează de asemenea câmpul `procentajProgres` bazându-se pe nr săptămâni cuprinse în tabela `PARCURS` (unde s-a făcut bifa) relativ la nr total curent.

5. `GET /cursuri/{cursId}/saptamani`
   - **Response:** `List<SaptamanaStudentResponseDto (id, nrSaptamana, descriere, finalizata)>`. (Include flag de stare "completat" sau nu pentru săptămâna respectivă din tabela `PARCURS`).

6. `POST /saptamani/{saptamanaId}/complete`
   - **Response:** 200 OK.
   - **Business Rules:** Adaugă rând în tabela `PARCURS` (marchează săptămâna la nivel student_curs=true). Validează prealabil ca studentul să fie înrolat.

7. `DELETE /saptamani/{saptamanaId}/complete`
   - **Response:** 200 OK.
   - **Business Rules:** Șterge rândul din tabela `PARCURS` pentru acest userCurs.

8. `GET /saptamani/{saptamanaId}/documente`
   - **Response:** `List<DocumentStudentResponseDto (id, titlu, urlVizualizare, urlDescarcare)>`. Listează doar `activ=true` (excluse cele sterse "soft" de prof).

9. `GET /cursuri/{cursId}/profesor`
   - **Response:** `ProfesorDetaliiResponseDto (id, nume, prenume, mail, facultate)`. Doar detalii publice profesor.

10. `GET /cursuri/{cursId}/documente-accesibile`
    - **Scop:** Listează documentele din săptămânile parcurse de student (<= `maxSaptamanaParcursa`).
    - **Response:** `List<AkySursaDocumentDto (id, titlu, nrSaptamana)>`.

### 7.2. `StudentQuizController` (`quiz/controller/`) — quiz-uri
1. `POST /cursuri/{cursId}/quiz/generate`
   - **Scop:** Generează un test grilă bazat pe documentul specificat (sau toate accesibile). Creează o intrare `IncercareQuiz` în starea `GENERATA`.
   - **Request DTO:** `QuizGenerateRequestDto (documentId, nrIntrebari)`.
   - **Response:** `QuizGenerateResponseDto (incercareId, intrebari(id, intrebare, optiuni))`.

2. `POST /quiz/{incercareId}/finalizeaza`
   - **Scop:** Finalizează o încercare curentă și evaluează scorul.
   - **Request DTO:** `FinalizeazaQuizRequestDto (raspunsuri(index, raspunsStudent))`.
   - **Response:** `QuizFinalizatResponseDto (incercareId, scor, nrIntrebari, procentaj, detalii)`.
   - **Excepții:** `IncercareQuizFinalizataException` dacă testul a fost deja marcat ca `FINALIZATA` (409, nu se re-finalizează).

3. `GET /quiz/istoric`
   - **Scop:** Listează istoricul testelor **finalizate** ale studentului curent.
   - **Query Params:** `cursId` (opțional), `page`, `size`.
   - **Response:** `Page<IncercareQuizSummaryDto>`.

4. `GET /quiz/istoric/{incercareId}`
   - **Scop:** Obține detaliile complete pentru o încercare de test.
   - **Response:** `IncercareQuizDetailDto`.

5. `DELETE /quiz/{incercareId}`
   - **Scop:** Șterge o încercare de quiz proprie, indiferent de status.
   - **Response:** 204 No Content.
   - **Excepții:** `ForbiddenOperationException` dacă nu e proprietarul; `ResursaNegasitaException` dacă id-ul nu există.

### 7.3. `StudentAkyController` (`akychat/controller/`) — chat Aky în timp real + flashcards
1. `POST /cursuri/{cursId}/chat`
   - **Request:** `AkyChatRequestDto (intrebare, istoricConversatie)`.
   - **Response:** `AkyChatResponseDto (raspuns, surseFolosite)`.
   - **Business Rules:** Rate limiting — maxim 10 cereri/minut, cheie comună cu quiz+flashcards (`"student-aky:" + studentId`, vezi `services.md` §9.1). Trimite către RAG `maxSaptamanaParcursa` calculat real din progresul studentului (vezi `contract-rag.md`).
   - **Excepții:** `TooManyRequestsException`.

2. `POST /cursuri/{cursId}/flashcards/generate`
   - **Scop:** Generează flashcards (Q&A) dintr-un material, apelând direct RAG.
   - **Request DTO:** `FlashcardGenerateRequestDto (documentId, nrFlashcards)`.
   - **Response:** `List<Map<String, Object>>` (JSON parsabil direct de frontend).

---

## 8. `ConversatieController`
- **Path de bază:** `/api`
- **Securitate:** `@PreAuthorize("hasAnyRole('STUDENT', 'PROFESOR')")`.

### Endpoints:
1. `GET /conversatii`
   - **Scop:** Returnează lista paginată a tuturor conversațiilor active ale utilizatorului logat.
   - **Query Params:** `page` (default 0), `size` (default 20).
   - **Response:** `ConversatiiPaginateDto (conversatii, paginaCurenta, totalPagini, totalElemente)`.

2. `GET /cursuri/{cursId}/conversatii`
   - **Scop:** Returnează conversațiile active ale utilizatorului specifice unui curs.
   - **Query Params:** `page` (default 0), `size` (default 20).
   - **Response:** `ConversatiiPaginateDto`.

3. `POST /cursuri/{cursId}/conversatii/mesaje`
   - **Scop:** Creează o nouă conversație pe un curs și trimite prima întrebare către RAG.
   - **Request DTO:** `NouaIntrebareRequest (intrebare)`. Validare: `@NotBlank`.
   - **Response:** `RagRaspunsResponse (conversatieId, mesaj)`.

4. `GET /conversatii/{id}/mesaje`
   - **Scop:** Obține istoricul de mesaje dintr-o conversație specifică.
   - **Query Params:** `inainteDe` (opțional, cursor ID mesaj), `limit` (default 20).
   - **Response:** `IstoricMesajeDto (mesaje, areMaiMulte)`.

5. `POST /conversatii/{id}/mesaje`
   - **Scop:** Adaugă o întrebare nouă într-o conversație existentă și primește răspunsul de la RAG.
   - **Request DTO:** `NouaIntrebareRequest (intrebare)`.
   - **Response:** `MesajChatDTO (id, rol, continut, surseFolosite, createdAt, areRaspuns)`.

6. `POST /conversatii/mesaje/{mesajId}/retry`
   - **Scop:** Reîncearcă obținerea răspunsului RAG pentru un mesaj al utilizatorului care a rămas nesoluționat (`areRaspuns = false`).
   - **Response:** `MesajChatDTO`.

7. `DELETE /conversatii/{id}`
   - **Response:** 204 No Content.
   - **Business Rules:** Soft-delete pe conversație (`activ = false`).

