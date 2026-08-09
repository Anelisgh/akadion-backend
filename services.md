# Documentație: Servicii (Pachetul `com.example.akadion.service`)

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
- **Securitate internă:** Majoritatea metodelor verifică ownership-ul: validează că `profesorId` primit coincide cu `curs.getProfesor().getId()`, altfel aruncă `AccesInterzisException`. Administratorul este by-passat pe read-only.

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
- **Responsabilități:** Activează / Dezactivează conturi utilizând ID-ul de Keycloak (`sub`), folosind fluxul OAuth2 Client Credentials configurat global.

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

## 9. `StudentCursService`
- **Scop:** Gestionarea înrolării studenților, tracking-ul bifa-rilor per săptămână, și rate limiting pentru chatbot.
- **Algoritmi importanți:**
  - `inscriereCurs`: Caută dacă există istoric `UserCurs`. Dacă da, face update `activ=true`. Dacă nu, inserție.
  - `Rate limiting pentru Chat`: Păstrează în memorie (sau folosește DB local) timestamp-urile mesajelor unui student. Permite maxim `MAX_MESSAGES_PER_MINUTE`.
  - `Calcul progres`: `countCompletedSaptamani / totalSaptamani * 100`.

## 10. `UserProfileService`
- **Scop:** Gestionarea profilului utilizatorului logat și actualizărilor acestuia (nume, email, reset parolă).
- **Workflow Actualizare Email:** Este un proces de tip "Distributed Saga". Schimbă emailul în Keycloak -> Schimbă emailul în DB locală cu `saveAndFlush`. Dacă apare eroare (ex: violare unique constraint în DB), compensează eroarea punând vechiul email înapoi în Keycloak.
- **Workflow Reset Parolă:** Apelează `KeycloakAdminService` pentru a executa o acțiune require de `UPDATE_PASSWORD`, trimițând utilizatorului un e-mail direct din Keycloak.
